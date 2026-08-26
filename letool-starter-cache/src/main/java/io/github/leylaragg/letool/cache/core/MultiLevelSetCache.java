package io.github.leylaragg.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.leylaragg.letool.redis.RedisUtil;
import io.github.leylaragg.letool.cache.exception.CacheException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 面向“一个 key 对应多个成员”的二级 Set 缓存。
 *
 * <p>普通 {@link MultiLevelCache} 适合 key -> value 的对象缓存；本类适合 key -> Set(member)
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
 * <p>注意：Set 成员直接交给 RedisTemplate 的 value serializer 处理。成员类型优先使用工厂方法
 * 显式传入的类型，其次使用 {@link CacheConfig#getValueType()}；两者都未配置时保留 RedisTemplate
 * 的实际反序列化类型。</p>
 *
 * @param <K> 业务 key 类型
 * @param <V> Set 成员类型
 */
public class MultiLevelSetCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelSetCache.class);
    /** 缓存区域名称，用于管理器注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 Set 缓存，key -> 并发安全的成员集合。 */
    private final Cache<K, Set<V>> l1Cache;
    /** Redis 操作工具。为 null 时该缓存退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** 当前 Set 缓存区域的 Redis 键空间。 */
    private final RedisCacheKeyspace keyspace;
    /** Redis Set 的过期时间。每次写入后都会补充 TTL，防止新 key 永不过期。 */
    private final Duration l2Ttl;
    /** 当前缓存实例是否启用 L1。 */
    private final boolean l1Enabled;
    /** 当前缓存实例是否启用 L2。 */
    private final boolean l2Enabled;
    /** 是否启用强一致读取。Set 缓存强一致模式下会优先读 Redis。 */
    private final boolean strongConsistency;
    /** Redis 读取失败后的返回或抛错策略。 */
    private final CacheReadFailurePolicy readFailurePolicy;
    /** Redis L2 变更失败后的处理策略。 */
    private final CacheWriteFailurePolicy writeFailurePolicy;
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
    /** Redis 故障后返回已有 L1 快照的次数。 */
    private final AtomicLong staleReadCount = new AtomicLong();
    /** Redis 故障后返回空集合的次数。 */
    private final AtomicLong failureEmptyCount = new AtomicLong();
    /** 严格策略因 Redis 故障拒绝返回结果的次数。 */
    private final AtomicLong failClosedCount = new AtomicLong();

    /** Redis 是否处于降级状态，降级后读写不再访问 L2。 */
    private volatile boolean l2Degraded = false;
    /** 最近一次触发降级的异常，供严格模式保留诊断原因链。 */
    private volatile Exception lastL2Failure;

    /**
     * 创建 Set 二级缓存实例。
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口
     * @param keySerializer 业务 key 序列化函数
     * @param memberType Redis 成员预期类型
     * @param invalidationPublisher L1 失效广播发布器
     * @param instanceId 当前 JVM 实例标识
     * @param degradationListener 首次降级回调
     */
    MultiLevelSetCache(CacheConfig<K, V> config,
                       RedisUtil redisUtil,
                       Function<K, String> keySerializer,
                       Class<V> memberType,
                       CacheInvalidationPublisher invalidationPublisher,
                       String instanceId,
                       Runnable degradationListener) {
        this.name = config.getName();
        this.redisUtil = redisUtil;
        this.keyspace = new RedisCacheKeyspace(config.getRedisKeyPrefix(), name);
        this.l2Ttl = config.getL2Ttl();
        this.l1Enabled = config.isL1Enabled();
        this.l2Enabled = redisUtil != null && config.isL2Enabled();
        this.strongConsistency = config.isStrongConsistency();
        this.readFailurePolicy = config.getReadFailurePolicy();
        this.writeFailurePolicy = config.getWriteFailurePolicy();
        this.keySerializer = keySerializer == null ? String::valueOf : keySerializer;
        this.memberType = resolveMemberType(memberType, config.getValueType());
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /**
     * 解析 Redis 成员的目标类型。
     *
     * @param explicitType 调用方显式指定的成员类型
     * @param configuredType 缓存配置声明的 value 类型
     * @param <T> 成员类型
     * @return 目标类型；均未指定时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> resolveMemberType(
            Class<T> explicitType,
            Class<?> configuredType) {
        return explicitType != null
                ? explicitType
                : (Class<T>) configuredType;
    }

    /**
     * 向指定 key 的集合中新增一个成员。
     *
     * <p>Redis 健康时不会为一次局部写入创建不完整 L1 快照；只有已有完整快照或 L2
     * 不可用时才更新本地数据。</p>
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param member 待新增成员；为 {@code null} 时忽略
     * @throws CacheException 严格写策略下 Redis 变更无法确认时抛出
     */
    public void add(K key, V member) {
        if (key == null || member == null) {
            return;
        }
        addCount.incrementAndGet();
        Set<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        // Redis 健康时，局部 add 只更新已有完整快照，不能凭单个成员创建伪完整 L1。
        boolean storedInL2 = saddToRedis(key, member);
        if (local != null) {
            local.add(member);
        } else if (l1Enabled && !storedInL2) {
            // L2 未启用或已经降级时，L1 成为当前节点的可用数据来源。
            getOrCreateLocalSet(key).add(member);
        }
        publishInvalidation(key);
    }

    /**
     * 批量新增成员。Redis Set 自身负责去重，Java 侧过滤 {@code null} 并提前去重。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param membersToAdd 待新增成员集合
     * @throws CacheException 严格写策略下 Redis 变更无法确认时抛出
     */
    public void addAll(K key, Collection<V> membersToAdd) {
        if (key == null || membersToAdd == null || membersToAdd.isEmpty()) {
            return;
        }
        Set<V> filtered = new HashSet<>();
        for (V member : membersToAdd) {
            if (member != null) {
                filtered.add(member);
            }
        }
        if (filtered.isEmpty()) {
            return;
        }
        addCount.addAndGet(filtered.size());
        Set<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        boolean storedInL2 = saddAllToRedis(key, filtered);
        if (local != null) {
            local.addAll(filtered);
        } else if (l1Enabled && !storedInL2) {
            getOrCreateLocalSet(key).addAll(filtered);
        }
        publishInvalidation(key);
    }

    /**
     * 删除指定成员。
     *
     * <p>删除会同步清理当前 JVM 的 L1，并通过 Redis/L1 失效广播影响其它 JVM。
     * 如果 Redis 已降级，则只清理当前进程本地副本。</p>
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param member 待删除成员；为 {@code null} 时忽略
     * @throws CacheException 严格写策略下 Redis 删除无法确认时抛出
     */
    public void remove(K key, V member) {
        if (key == null || member == null) {
            return;
        }
        if (isStrictWriteFailurePolicy() && l2Enabled) {
            sremFromRedis(key, member);
            removeLocalMember(key, member);
        } else {
            removeLocalMember(key, member);
            sremFromRedis(key, member);
        }
        removeCount.incrementAndGet();
        publishInvalidation(key);
    }

    private void removeLocalMember(K key, V member) {
        if (l1Enabled) {
            Set<V> members = l1Cache.getIfPresent(key);
            if (members != null) {
                members.remove(member);
            }
        }
    }

    /**
     * 删除整个 key 对应的集合。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @throws CacheException 严格写策略下 Redis 删除无法确认时抛出
     */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        if (isStrictWriteFailurePolicy() && l2Enabled) {
            deleteFromRedis(key);
            evictLocal(key);
        } else {
            evictLocal(key);
            deleteFromRedis(key);
        }
        publishInvalidation(key);
    }

    /**
     * 获取指定 key 的成员快照。
     *
     * <p>返回值始终是新的 {@link HashSet}，调用方修改返回集合不会污染缓存内部状态。
     * 在强一致模式下，会优先读取 Redis；Redis 不可用时才退回已有 L1 副本。</p>
     *
     * @param key 业务 key
     * @return 成员快照；key 无效或不存在时返回空集合
     */
    public Set<V> getMembers(K key) {
        // 保留 2.1.x 可修改快照契约；带状态 API 自身返回不可变集合。
        return new HashSet<>(getMembersWithStatus(key).members());
    }

    /**
     * 获取成员快照及其可信状态。
     *
     * <p>Redis 正常返回的空集合仍是权威结果；只有读取失败时才按配置选择
     * 陈旧快照、故障空结果或抛出统一异常。</p>
     *
     * @param key 业务 key
     * @return 不可变成员快照及结果状态
     * @throws CacheException 严格策略下 Redis 读取失败时抛出
     */
    public SetCacheReadResult<V> getMembersWithStatus(K key) {
        if (key == null) {
            return authoritative(Collections.emptySet());
        }
        Set<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return authoritative(local);
        }
        if (l2Enabled && !l2Degraded) {
            try {
                Set<V> l2Members = readMembersFromRedis(key);
                // Redis 成功返回的空 Set 同样是权威结果，不能回退到旧 L1。
                if (l2Members.isEmpty()) {
                    missCount.incrementAndGet();
                } else {
                    l2HitCount.incrementAndGet();
                }
                if (l1Enabled) {
                    l1Cache.put(key, concurrentSnapshot(l2Members));
                }
                return authoritative(l2Members);
            } catch (Exception e) {
                markL2Degraded(e);
                return handleReadFailure(local);
            }
        }
        if (l2Enabled) {
            return handleReadFailure(local);
        }
        if (local != null) {
            l1HitCount.incrementAndGet();
            return authoritative(local);
        }
        missCount.incrementAndGet();
        return authoritative(Collections.emptySet());
    }

    /**
     * 判断指定 key 的集合中是否包含某个成员。
     *
     * @param key 业务 key
     * @param member 待判断成员
     * @return 存在返回 {@code true}，参数无效或不存在返回 {@code false}
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
            try {
                boolean present = readMembershipFromRedis(key, member);
                Set<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
                if (local != null) {
                    if (present) {
                        local.add(member);
                    } else {
                        local.remove(member);
                    }
                }
                return present;
            } catch (Exception e) {
                markL2Degraded(e);
                Set<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
                return handleReadFailure(local).members().contains(member);
            }
        }
        Set<V> members = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (l2Enabled) {
            return handleReadFailure(members).members().contains(member);
        }
        return members != null && members.contains(member);
    }

    /**
     * 清空当前缓存区域的所有 L1/L2 数据，并广播其它 JVM 清理本地副本。
     *
     * <p>始终先清理当前 JVM 的 L1。启用 L2 时，Redis 使用 SCAN + UNLINK 分批清理，
     * 只有完整成功后才广播；L1-only 模式在本地清理完成后广播。</p>
     *
     * @throws CacheException L2 已降级或 Redis 区域清理失败时抛出
     */
    public void evictAll() {
        evictLocalAll();
        if (l2Enabled) {
            if (l2Degraded) {
                throw CacheException.l2Unavailable(lastL2Failure);
            }
            try {
                keyspace.scanAndUnlink(redisUtil.getTemplate());
            } catch (Exception e) {
                markL2Degraded(e);
                throw CacheException.l2Unavailable(e);
            }
        }
        publishInvalidationAll();
    }

    /**
     * 按稳定序列化后的业务 key 前缀清理 Set 缓存。
     *
     * <p>本机 L1 先清理，Redis 使用 SCAN + UNLINK 限定在当前区域和业务前缀，
     * 成功后发布一条 PREFIX 消息通知其它 JVM。</p>
     *
     * @param serializedBusinessKeyPrefix 业务 key 序列化前缀
     * @throws CacheException 前缀为空或 Redis 清理失败时抛出
     */
    public void evictByPrefix(String serializedBusinessKeyPrefix) {
        if (serializedBusinessKeyPrefix == null
                || serializedBusinessKeyPrefix.isBlank()) {
            throw CacheException.configurationInvalid("serialized-business-key-prefix");
        }
        evictLocalSerializedPrefix(serializedBusinessKeyPrefix);
        if (l2Enabled) {
            if (l2Degraded) {
                throw CacheException.l2Unavailable(lastL2Failure);
            }
            try {
                keyspace.scanAndUnlink(
                        redisUtil.getTemplate(), serializedBusinessKeyPrefix);
            } catch (Exception exception) {
                markL2Degraded(exception);
                throw CacheException.l2Unavailable(exception);
            }
        }
        invalidationPublisher.publish(CacheInvalidationMessage.prefix(
                name, serializedBusinessKeyPrefix, instanceId));
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
     * 按广播中的序列化表示匹配并清理真实 L1 key。
     *
     * @param serializedKey 广播中的业务 key 字符串
     */
    void evictLocalSerializedKey(String serializedKey) {
        if (!l1Enabled || serializedKey == null) {
            return;
        }
        for (K candidate : l1Cache.asMap().keySet()) {
            if (serializedKey.equals(keySerializer.apply(candidate))) {
                l1Cache.invalidate(candidate);
            }
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
     *
     * <p>探测成功后会清空降级期间形成的本地快照，后续读取重新以 Redis 为准。</p>
     *
     * @return 已处于健康状态或本次恢复成功时返回 {@code true}
     */
    public boolean tryRecoverL2() {
        if (!l2Degraded) {
            return true;
        }
        try {
            redisUtil.hasKey(keyspace.healthCheckKey());
            evictLocalAll();
            l2Degraded = false;
            lastL2Failure = null;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** @return 当前 L1 中估算的业务 key 数量 */
    public long estimatedSize() {
        return l1Enabled ? l1Cache.estimatedSize() : 0;
    }

    /** @return 当前 Set 缓存运行统计快照 */
    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                addCount.get(), removeCount.get(), staleReadCount.get(),
                failureEmptyCount.get(), failClosedCount.get(), estimatedSize(),
                l2Degraded);
    }

    private Set<V> getOrCreateLocalSet(K key) {
        // Caffeine 的 get(key, mappingFunction) 能保证同一个 key 只创建一个 Set 实例。
        return l1Cache.get(key, ignored -> ConcurrentHashMap.newKeySet());
    }

    /**
     * 创建线程安全的完整成员快照。
     *
     * @param source Redis 返回的完整成员集合
     * @return 可由本类内部安全更新的并发集合
     */
    private Set<V> concurrentSnapshot(Set<V> source) {
        Set<V> snapshot = ConcurrentHashMap.newKeySet(
                Math.max(1, source.size())
        );
        snapshot.addAll(source);
        return snapshot;
    }

    private String redisKey(K key) {
        return keyspace.key(keySerializer.apply(key));
    }

    private boolean saddToRedis(K key, V member) {
        if (!l2Enabled) {
            return false;
        }
        requireWritableL2IfStrict();
        if (l2Degraded) {
            return false;
        }
        try {
            Long added = redisUtil.boundSetOps(redisKey(key)).add(member);
            if (added == null) {
                throw CacheWriteFailureSupport.unconfirmed("SADD");
            }
        } catch (Exception e) {
            return handleWriteFailure(e);
        }
        return setTtl(key);
    }

    private boolean saddAllToRedis(K key, Collection<V> members) {
        if (!l2Enabled) {
            return false;
        }
        requireWritableL2IfStrict();
        if (l2Degraded) {
            return false;
        }
        try {
            Object[] values = members.stream()
                    .filter(member -> member != null)
                    .toArray();
            if (values.length > 0) {
                Long added = redisUtil.boundSetOps(redisKey(key)).add(values);
                if (added == null) {
                    throw CacheWriteFailureSupport.unconfirmed("SADD");
                }
            } else {
                return true;
            }
        } catch (Exception e) {
            return handleWriteFailure(e);
        }
        return setTtl(key);
    }

    private boolean sremFromRedis(K key, V member) {
        if (!l2Enabled) {
            return true;
        }
        requireWritableL2IfStrict();
        if (l2Degraded) {
            return false;
        }
        try {
            Long removed = redisUtil.boundSetOps(redisKey(key)).remove(member);
            if (removed == null) {
                throw CacheWriteFailureSupport.unconfirmed("SREM");
            }
            return true;
        } catch (Exception e) {
            return handleWriteFailure(e);
        }
    }

    private Set<V> readMembersFromRedis(K key) {
        Set<Object> raw = redisUtil.boundSetOps(redisKey(key)).members();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<V> result = ConcurrentHashMap.newKeySet(raw.size());
        for (Object item : raw) {
            V member = deserializeMember(item);
            if (member != null) {
                result.add(member);
            }
        }
        return result;
    }

    /** 按稳定序列化结果的前缀清理本机 L1 条目。 */
    void evictLocalSerializedPrefix(String serializedPrefix) {
        if (!l1Enabled || serializedPrefix == null || serializedPrefix.isBlank()) {
            return;
        }
        for (K candidate : l1Cache.asMap().keySet()) {
            String serialized = keySerializer.apply(candidate);
            if (serialized != null && serialized.startsWith(serializedPrefix)) {
                l1Cache.invalidate(candidate);
            }
        }
    }

    private boolean readMembershipFromRedis(K key, V member) {
        return Boolean.TRUE.equals(redisUtil.boundSetOps(redisKey(key)).isMember(member));
    }

    private boolean deleteFromRedis(K key) {
        if (!l2Enabled) {
            return true;
        }
        requireWritableL2IfStrict();
        if (l2Degraded) {
            return false;
        }
        try {
            redisUtil.delete(redisKey(key));
            return true;
        } catch (Exception e) {
            return handleWriteFailure(e);
        }
    }

    private boolean setTtl(K key) {
        try {
            // Redis SADD 不会自动设置过期时间，因此每次写入后补充 TTL。
            if (!redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS)) {
                throw CacheWriteFailureSupport.unconfirmed("PEXPIRE");
            }
            return true;
        } catch (Exception e) {
            return handleWriteFailure(e);
        }
    }

    private boolean handleWriteFailure(Exception cause) {
        markL2Degraded(cause);
        CacheWriteFailureSupport.throwIfStrict(writeFailurePolicy, cause);
        return false;
    }

    /** 严格策略不允许把已经降级的 L2 变更伪装成本地成功。 */
    private void requireWritableL2IfStrict() {
        if (l2Degraded) {
            CacheWriteFailureSupport.throwIfStrict(writeFailurePolicy, lastL2Failure);
        }
    }

    private boolean isStrictWriteFailurePolicy() {
        return writeFailurePolicy == CacheWriteFailurePolicy.FAIL_CLOSED;
    }

    @SuppressWarnings("unchecked")
    private V deserializeMember(Object raw) {
        if (raw == null) {
            return null;
        }
        if (memberType == null) {
            return (V) raw;
        }
        if (memberType.isInstance(raw)) {
            return memberType.cast(raw);
        }
        String value = raw.toString();
        if (String.class.equals(memberType)) {
            return memberType.cast(value);
        }
        if (Integer.class.equals(memberType)) {
            try {
                return memberType.cast(Integer.valueOf(value));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (Long.class.equals(memberType)) {
            try {
                return memberType.cast(Long.valueOf(value));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private void publishInvalidation(K key) {
        invalidationPublisher.publish(CacheInvalidationMessage.keys(
                name, java.util.List.of(keySerializer.apply(key)), instanceId));
    }

    private void publishInvalidationAll() {
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    private void markL2Degraded(Exception e) {
        lastL2Failure = e;
        if (!l2Degraded) {
            l2Degraded = true;
            // 首次降级时登记到 CacheManager，后续由恢复调度器统一探测。
            degradationListener.run();
            log.warn(
                    "Set cache [{}] L2 degraded, causeType={}",
                    name,
                    e.getClass().getSimpleName()
            );
            log.debug("Set cache L2 degradation detail", e);
        }
    }

    private SetCacheReadResult<V> authoritative(Set<V> members) {
        return new SetCacheReadResult<>(members, SetCacheReadResult.State.AUTHORITATIVE);
    }

    private SetCacheReadResult<V> handleReadFailure(Set<V> local) {
        if (readFailurePolicy == CacheReadFailurePolicy.FAIL_CLOSED) {
            failClosedCount.incrementAndGet();
            throw CacheException.l2Unavailable(lastL2Failure);
        }
        if (readFailurePolicy == CacheReadFailurePolicy.STALE_IF_AVAILABLE
                && local != null) {
            staleReadCount.incrementAndGet();
            l1HitCount.incrementAndGet();
            return new SetCacheReadResult<>(local, SetCacheReadResult.State.STALE);
        }
        failureEmptyCount.incrementAndGet();
        missCount.incrementAndGet();
        return new SetCacheReadResult<>(
                Collections.emptySet(),
                SetCacheReadResult.State.FAILURE_EMPTY
        );
    }

    /**
     * Set 缓存运行统计快照。
     *
     * @param name 缓存区域名称
     * @param l1HitCount L1 命中次数
     * @param l2HitCount L2 命中次数
     * @param missCount 未命中次数
     * @param addCount 新增成员计数
     * @param removeCount 删除成员计数
     * @param staleReadCount Redis 故障后返回 L1 快照的次数
     * @param failureEmptyCount Redis 故障后返回空集合的次数
     * @param failClosedCount 严格策略拒绝返回结果的次数
     * @param l1Size L1 业务 key 估算数量
     * @param l2Degraded L2 是否处于降级状态
     */
    public record Stats(String name,
                        long l1HitCount,
                        long l2HitCount,
                        long missCount,
                        long addCount,
                        long removeCount,
                        long staleReadCount,
                        long failureEmptyCount,
                        long failClosedCount,
                        long l1Size,
                        boolean l2Degraded) {
        /** @return L1 命中、L2 命中和未命中的总次数 */
        public long totalRequests() {
            return l1HitCount + l2HitCount + missCount;
        }
    }
}
