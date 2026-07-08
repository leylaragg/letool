package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 面向“一个 key 对应多个成员”的二级 Set 缓存。
 *
 * <p>普通 {@link DefaultMultiLevelCache} 适合 key -> value 的对象缓存；本类适合 key -> Set(member)
 * 的索引类缓存，例如：</p>
 * <ul>
 *     <li>项目版本 -> 规则 ID 集合</li>
 *     <li>用户 -> 权限标识集合</li>
 *     <li>业务分组 -> 关联对象编号集合</li>
 * </ul>
 *
 * <p>实现策略：</p>
 * <ul>
 *     <li>L1 使用 Caffeine 保存线程安全 Set，读性能高。</li>
 *     <li>L2 使用 Redis Set，跨 JVM 共享成员集合。</li>
 *     <li>写入/删除后会发布 L1 失效消息，通知其它 JVM 清理本地 Set 副本。</li>
 *     <li>强一致模式下读取优先走 Redis，不直接相信 L1，避免集合成员变更后读到旧快照。</li>
 * </ul>
 *
 * <p>注意：Set 成员在 Redis 中统一按字符串保存，读取时再根据 {@code memberType} 转成业务类型。
 * 如果业务成员不是 Long，建议显式传入成员类型，避免默认 Long 转换不符合预期。</p>
 */
public class MultiLevelSetCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelSetCache.class);
    /** RedisUtil 没有暴露 SREM 专用方法时，使用 Lua 保持本 starter 对 RedisUtil 的旧 API 兼容。 */
    private static final String SREM_SCRIPT = "return redis.call('SREM', KEYS[1], ARGV[1])";

    /** 缓存区域名称，用于管理器注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 Set 缓存，key -> 并发安全的成员集合。 */
    private final Cache<K, Set<V>> l1Cache;
    /** Redis 操作工具。为 null 时该缓存退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** Redis key 前缀，最终 Redis key = redisKeyPrefix + keySerializer.apply(key)。 */
    private final String redisKeyPrefix;
    /** Redis Set 的过期时间。每次写入后都会补充 TTL，防止新 key 永不过期。 */
    private final Duration l2Ttl;
    /** 当前缓存实例是否启用 L1。 */
    private final boolean l1Enabled;
    /** 当前缓存实例是否启用 L2。 */
    private final boolean l2Enabled;
    /** 是否启用强一致读取。Set 缓存强一致模式下会优先读 Redis。 */
    private final boolean strongConsistency;
    /** 业务 key 到 Redis key 后缀的转换函数。 */
    private final Function<K, String> keySerializer;
    /** Redis Set 成员读取后的目标类型。 */
    private final Class<V> memberType;
    /** 跨 JVM L1 失效广播发布器。 */
    private final CacheInvalidationPublisher invalidationPublisher;
    /** 当前 JVM 缓存节点 ID，用于忽略自己发出的失效广播。 */
    private final String instanceId;
    /** 首次 Redis 异常进入降级时，通知 CacheManager 记录待恢复缓存。 */
    private final Runnable degradationListener;

    /** L1 命中次数。 */
    private final AtomicLong l1HitCount = new AtomicLong();
    /** L2 命中次数。 */
    private final AtomicLong l2HitCount = new AtomicLong();
    /** L1/L2 都未命中的次数。 */
    private final AtomicLong missCount = new AtomicLong();
    /** 新增成员请求计数。 */
    private final AtomicLong addCount = new AtomicLong();
    /** 删除成员请求计数。 */
    private final AtomicLong removeCount = new AtomicLong();

    /** Redis 是否处于降级状态，降级后读写不再访问 L2。 */
    private volatile boolean l2Degraded = false;

    MultiLevelSetCache(CacheConfig<K, V> config,
                       RedisUtil redisUtil,
                       Function<K, String> keySerializer,
                       Class<V> memberType,
                       CacheInvalidationPublisher invalidationPublisher,
                       String instanceId,
                       Runnable degradationListener) {
        this.name = config.getName();
        this.redisUtil = redisUtil;
        this.redisKeyPrefix = config.getRedisKeyPrefix();
        this.l2Ttl = config.getL2Ttl();
        this.l1Enabled = config.isL1Enabled();
        this.l2Enabled = redisUtil != null && config.isL2Enabled();
        this.strongConsistency = config.isStrongConsistency();
        this.keySerializer = keySerializer == null ? String::valueOf : keySerializer;
        this.memberType = memberType == null ? defaultMemberType() : memberType;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> defaultMemberType() {
        // EDC 规则索引场景中成员主要是规则 ID，因此默认按 Long 转换。
        return (Class<T>) Long.class;
    }

    /**
     * 向指定 key 的集合中新增一个成员。
     *
     * <p>本方法先更新当前 JVM 的 L1，再写 Redis，最后广播其它 JVM 清理本地副本。
     * 这样当前节点能立刻读到刚写入的成员，其它节点通过失效消息或 TTL 保持一致。</p>
     */
    public void add(K key, V member) {
        if (key == null || member == null) {
            return;
        }
        if (l1Enabled) {
            Set<V> members = getOrCreateLocalSet(key);
            if (!members.add(member)) {
                // 本地已存在时不重复写 Redis，减少无意义网络操作。
                return;
            }
        }
        addCount.incrementAndGet();
        saddToRedis(key, member);
        publishInvalidation(key);
    }

    /**
     * 批量新增成员。Redis Set 自身负责去重，Java 侧只过滤 null。
     */
    public void addAll(K key, Collection<V> membersToAdd) {
        if (key == null || membersToAdd == null || membersToAdd.isEmpty()) {
            return;
        }
        int added = 0;
        if (l1Enabled) {
            Set<V> members = getOrCreateLocalSet(key);
            for (V member : membersToAdd) {
                if (member != null && members.add(member)) {
                    added++;
                }
            }
        } else {
            added = (int) membersToAdd.stream().filter(member -> member != null).count();
        }
        addCount.addAndGet(added);
        saddAllToRedis(key, membersToAdd);
        publishInvalidation(key);
    }

    /**
     * 删除指定成员。
     *
     * <p>删除会同步清理当前 JVM 的 L1，并通过 Redis/L1 失效广播影响其它 JVM。
     * 如果 Redis 已降级，则只清理当前进程本地副本。</p>
     */
    public void remove(K key, V member) {
        if (key == null || member == null) {
            return;
        }
        if (l1Enabled) {
            Set<V> members = l1Cache.getIfPresent(key);
            if (members != null) {
                members.remove(member);
            }
        }
        removeCount.incrementAndGet();
        sremFromRedis(key, member);
        publishInvalidation(key);
    }

    /**
     * 删除整个 key 对应的集合。
     */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        evictLocal(key);
        deleteFromRedis(key);
        publishInvalidation(key);
    }

    /**
     * 获取指定 key 的成员快照。
     *
     * <p>返回值始终是新的 {@link HashSet}，调用方修改返回集合不会污染缓存内部状态。
     * 在强一致模式下，会优先读取 Redis；Redis 不可用时才退回已有 L1 副本。</p>
     */
    public Set<V> getMembers(K key) {
        if (key == null) {
            return Collections.emptySet();
        }
        Set<V> local = l1Enabled && !strongConsistency ? l1Cache.getIfPresent(key) : l1Cache.getIfPresent(key);
        if (local != null && !local.isEmpty() && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return new HashSet<>(local);
        }
        if (l2Enabled && !l2Degraded) {
            Set<V> l2Members = smembersFromRedis(key);
            if (l2Members != null && !l2Members.isEmpty()) {
                l2HitCount.incrementAndGet();
                if (l1Enabled) {
                    // Redis 命中后回填本地 Set，后续最终一致读取可以直接命中 L1。
                    l1Cache.put(key, ConcurrentHashMap.newKeySet(l2Members.size()));
                    Set<V> refill = l1Cache.getIfPresent(key);
                    if (refill != null) {
                        refill.addAll(l2Members);
                    }
                }
                return new HashSet<>(l2Members);
            }
        }
        if (local != null && !local.isEmpty()) {
            l1HitCount.incrementAndGet();
            return new HashSet<>(local);
        }
        missCount.incrementAndGet();
        return Collections.emptySet();
    }

    /**
     * 判断指定 key 的集合中是否包含某个成员。
     */
    public boolean contains(K key, V member) {
        if (key == null || member == null) {
            return false;
        }
        if (l1Enabled && !strongConsistency) {
            Set<V> members = l1Cache.getIfPresent(key);
            if (members != null) {
                return members.contains(member);
            }
        }
        if (l2Enabled && !l2Degraded) {
            return sismemberInRedis(key, member);
        }
        Set<V> members = l1Enabled ? l1Cache.getIfPresent(key) : null;
        return members != null && members.contains(member);
    }

    /**
     * 清空当前缓存区域的所有 L1/L2 数据，并广播其它 JVM 清理本地副本。
     */
    public void evictAll() {
        evictLocalAll();
        if (l2Enabled && !l2Degraded) {
            try {
                Set<String> keys = redisUtil.getTemplate().keys(redisKeyPrefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisUtil.delete(keys);
                }
            } catch (Exception e) {
                markL2Degraded(e);
            }
        }
        publishInvalidationAll();
    }

    /**
     * 仅清理当前 JVM 的某个 L1 条目，供失效监听器调用。
     */
    void evictLocal(K key) {
        if (l1Enabled && key != null) {
            l1Cache.invalidate(key);
        }
    }

    /**
     * 仅清空当前 JVM 的 L1 区域，供失效监听器调用。
     */
    void evictLocalAll() {
        if (l1Enabled) {
            l1Cache.invalidateAll();
        }
    }

    boolean isL2Degraded() {
        return l2Degraded;
    }

    /**
     * 尝试恢复 Redis L2。该方法只做轻量探测，不预热数据。
     */
    public boolean tryRecoverL2() {
        if (!l2Degraded) {
            return true;
        }
        try {
            redisUtil.hasKey(redisKeyPrefix + "__health_check");
            l2Degraded = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long estimatedSize() {
        return l1Enabled ? l1Cache.estimatedSize() : 0;
    }

    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                addCount.get(), removeCount.get(), estimatedSize(), l2Degraded);
    }

    private Set<V> getOrCreateLocalSet(K key) {
        // Caffeine 的 get(key, mappingFunction) 能保证同一个 key 只创建一个 Set 实例。
        return l1Cache.get(key, ignored -> ConcurrentHashMap.newKeySet());
    }

    private String redisKey(K key) {
        return redisKeyPrefix + keySerializer.apply(key);
    }

    private void saddToRedis(K key, V member) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.sadd(redisKey(key), serializeMember(member));
            setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void saddAllToRedis(K key, Collection<V> members) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            String[] values = members.stream()
                    .filter(member -> member != null)
                    .map(this::serializeMember)
                    .toArray(String[]::new);
            if (values.length > 0) {
                redisUtil.sadd(redisKey(key), values);
                setTtl(key);
            }
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void sremFromRedis(K key, V member) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.executeScript(SREM_SCRIPT, List.of(redisKey(key)), serializeMember(member));
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private Set<V> smembersFromRedis(K key) {
        try {
            Set<String> raw = redisUtil.smembers(redisKey(key));
            if (raw == null || raw.isEmpty()) {
                return Collections.emptySet();
            }
            Set<V> result = ConcurrentHashMap.newKeySet(raw.size());
            for (String item : raw) {
                V member = deserializeMember(item);
                if (member != null) {
                    result.add(member);
                }
            }
            return result;
        } catch (Exception e) {
            markL2Degraded(e);
            return Collections.emptySet();
        }
    }

    private boolean sismemberInRedis(K key, V member) {
        try {
            return redisUtil.sismember(redisKey(key), serializeMember(member));
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private void deleteFromRedis(K key) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.delete(redisKey(key));
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void setTtl(K key) {
        try {
            // Redis SADD 不会自动设置过期时间，因此每次写入后补充 TTL。
            redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Failed to set Redis Set TTL for cache [{}], key [{}]", name, key, e);
        }
    }

    private String serializeMember(V member) {
        return String.valueOf(member);
    }

    @SuppressWarnings("unchecked")
    private V deserializeMember(Object raw) {
        if (raw == null) {
            return null;
        }
        if (memberType.isInstance(raw)) {
            return memberType.cast(raw);
        }
        String value = raw.toString();
        if (String.class.equals(memberType)) {
            return memberType.cast(value);
        }
        if (Long.class.equals(memberType)) {
            return memberType.cast(Long.valueOf(value));
        }
        if (Integer.class.equals(memberType)) {
            return memberType.cast(Integer.valueOf(value));
        }
        return (V) value;
    }

    private void publishInvalidation(K key) {
        invalidationPublisher.publish(CacheInvalidationMessage.keys(
                name, java.util.List.of(keySerializer.apply(key)), instanceId));
    }

    private void publishInvalidationAll() {
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    private void markL2Degraded(Exception e) {
        if (!l2Degraded) {
            l2Degraded = true;
            // 首次降级时登记到 CacheManager，后续由恢复调度器统一探测。
            degradationListener.run();
            log.warn("Set cache [{}] L2 degraded: {}", name, e.getMessage());
        }
    }

    /**
     * Set 缓存运行统计快照。
     */
    public record Stats(String name,
                        long l1HitCount,
                        long l2HitCount,
                        long missCount,
                        long addCount,
                        long removeCount,
                        long l1Size,
                        boolean l2Degraded) {
        public long totalRequests() {
            return l1HitCount + l2HitCount + missCount;
        }
    }
}
