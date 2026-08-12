package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 面向 Redis ZSet 语义的二级缓存。
 *
 * <p>适合排行榜、权重排序、优先级队列等“一个业务 key 对应多个带分数成员”的场景。
 * L2 使用 Redis ZSet 原生结构，member 由 RedisTemplate 序列化器处理，score 使用 Redis 原生 double。</p>
 *
 * @param <K> 业务 key 类型
 * @param <V> ZSet 成员类型
 */
public class MultiLevelZSetCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelZSetCache.class);

    /** 缓存区域名称，用于注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 ZSet 快照，key -> member/score。 */
    private final Cache<K, Map<V, Double>> l1Cache;
    /** Redis 操作工具。为 null 时退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** 当前 ZSet 缓存区域的 Redis 键空间。 */
    private final RedisCacheKeyspace keyspace;
    /** Redis ZSet 的过期时间，每次写入后都会补充 TTL。 */
    private final Duration l2Ttl;
    /** 当前缓存实例是否启用 L1。 */
    private final boolean l1Enabled;
    /** 当前缓存实例是否启用 L2。 */
    private final boolean l2Enabled;
    /** 是否启用强一致读取；开启后读取优先走 Redis。 */
    private final boolean strongConsistency;
    /** 业务 key 到 Redis key 后缀的转换函数。 */
    private final Function<K, String> keySerializer;
    /** ZSet member 读取后的目标类型。 */
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
    /** L1/L2 未命中次数。 */
    private final AtomicLong missCount = new AtomicLong();
    /** 新增或更新成员计数。 */
    private final AtomicLong addCount = new AtomicLong();
    /** 删除成员计数。 */
    private final AtomicLong removeCount = new AtomicLong();

    /** Redis L2 当前是否处于降级状态。 */
    private volatile boolean l2Degraded = false;

    /**
     * 创建 ZSet 二级缓存实例。
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口
     * @param keySerializer 业务 key 序列化函数
     * @param memberType Redis 成员预期类型
     * @param invalidationPublisher L1 失效广播发布器
     * @param instanceId 当前 JVM 实例标识
     * @param degradationListener 首次降级回调
     */
    MultiLevelZSetCache(CacheConfig<K, V> config,
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
        this.keySerializer = keySerializer == null ? String::valueOf : keySerializer;
        this.memberType = resolveMemberType(
                memberType,
                config.getValueType()
        );
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /**
     * 解析 Redis ZSet 成员的目标类型。
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
     * 添加或更新一个 ZSet 成员的分数。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param member ZSet 成员；为 {@code null} 时忽略
     * @param score 成员分数
     */
    public void add(K key, V member, double score) {
        if (key == null || member == null) {
            return;
        }
        addCount.incrementAndGet();
        Map<V, Double> local =
                l1Enabled ? l1Cache.getIfPresent(key) : null;
        // Redis 健康时，局部 add 只更新已有完整快照，不能凭单个成员创建伪完整 L1。
        boolean storedInL2 = addToRedis(key, member, score);
        if (local != null) {
            local.put(member, score);
        } else if (l1Enabled && !storedInL2) {
            getOrCreateLocalScores(key).put(member, score);
        }
        publishInvalidation(key);
    }

    /**
     * 删除指定 ZSet 成员，并清理当前 JVM 的 L1 快照。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param member 待删除成员；为 {@code null} 时忽略
     */
    public void remove(K key, V member) {
        if (key == null || member == null) {
            return;
        }
        if (l1Enabled) {
            Map<V, Double> local = l1Cache.getIfPresent(key);
            if (local != null) {
                local.remove(member);
            }
        }
        removeCount.incrementAndGet();
        removeFromRedis(key, member);
        publishInvalidation(key);
    }

    /**
     * 获取指定成员的分数；强一致模式下优先读取 Redis。
     *
     * @param key 业务 key
     * @param member ZSet 成员
     * @return 成员分数；参数无效或成员不存在时返回 {@code null}
     */
    public Double score(K key, V member) {
        if (key == null || member == null) {
            return null;
        }
        Map<V, Double> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return local.get(member);
        }
        if (l2Enabled && !l2Degraded) {
            Double score = scoreFromRedis(key, member);
            if (!l2Degraded) {
                if (score == null) {
                    missCount.incrementAndGet();
                } else {
                    l2HitCount.incrementAndGet();
                }
                if (local != null) {
                    if (score == null) {
                        local.remove(member);
                    } else {
                        local.put(member, score);
                    }
                }
                return score;
            }
        }
        if (local != null && local.containsKey(member)) {
            l1HitCount.incrementAndGet();
            return local.get(member);
        }
        missCount.incrementAndGet();
        return null;
    }

    /**
     * 获取排名范围内的成员快照；索引语义与 Redis ZRANGE 一致。
     *
     * <p>Redis 读取使用带分数的批量结果，避免逐成员执行 ZSCORE。</p>
     *
     * @param key 业务 key
     * @param start 起始排名索引，支持负数
     * @param end 结束排名索引，支持负数且包含该位置
     * @return 排名范围内的有序成员快照
     */
    public Set<V> range(K key, long start, long end) {
        if (key == null) {
            return Set.of();
        }
        Map<V, Double> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return rangeLocal(local, start, end);
        }
        if (l2Enabled && !l2Degraded) {
            Map<V, Double> scores = rangeFromRedis(key, start, end);
            if (!l2Degraded) {
                // Redis 成功返回的空 ZSet 同样是权威结果，不能回退到旧 L1。
                if (scores.isEmpty()) {
                    missCount.incrementAndGet();
                } else {
                    l2HitCount.incrementAndGet();
                }
                if (l1Enabled && start == 0 && end == -1) {
                    l1Cache.put(key, new ConcurrentHashMap<>(scores));
                }
                return new LinkedHashSet<>(scores.keySet());
            }
        }
        if (local != null) {
            l1HitCount.incrementAndGet();
            return rangeLocal(local, start, end);
        }
        missCount.incrementAndGet();
        return Set.of();
    }

    /**
     * 删除整个业务 key 对应的 ZSet。
     *
     * @param key 业务 key；为 {@code null} 时忽略
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
     * 清空当前 ZSet 缓存区域的全部 L1/L2 数据，并广播其它 JVM 清理本地快照。
     *
     * <p>Redis L2 使用 SCAN + UNLINK 分批清理，只匹配当前缓存名称对应的键空间。</p>
     */
    public void evictAll() {
        evictLocalAll();
        if (l2Enabled && !l2Degraded) {
            try {
                keyspace.scanAndUnlink(redisUtil.getTemplate());
            } catch (Exception e) {
                markL2Degraded(e);
            }
        }
        publishInvalidationAll();
    }

    /** 仅清理当前 JVM 的某个 L1 条目，供失效监听器调用。 */
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

    /** 仅清空当前 JVM 的 L1 区域，供失效监听器调用。 */
    void evictLocalAll() {
        if (l1Enabled) {
            l1Cache.invalidateAll();
        }
    }

    boolean isL2Degraded() {
        return l2Degraded;
    }

    /**
     * 尝试恢复 Redis L2；该方法只做轻量探测，不预热数据。
     *
     * <p>探测成功后会清空降级期间形成的本地快照。</p>
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
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** @return 当前 ZSet 缓存运行统计快照 */
    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                addCount.get(), removeCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private Map<V, Double> getOrCreateLocalScores(K key) {
        return l1Cache.get(key, ignored -> new ConcurrentHashMap<>());
    }

    private boolean addToRedis(K key, V member, double score) {
        if (!l2Enabled || l2Degraded) {
            return false;
        }
        try {
            redisUtil.boundZSetOps(redisKey(key)).add(member, score);
            return setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private void removeFromRedis(K key, V member) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.boundZSetOps(redisKey(key)).remove(member);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private Double scoreFromRedis(K key, V member) {
        try {
            return redisUtil.boundZSetOps(redisKey(key)).score(member);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Map<V, Double> rangeFromRedis(K key, long start, long end) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    redisUtil.boundZSetOps(redisKey(key))
                            .rangeWithScores(start, end);
            if (tuples == null || tuples.isEmpty()) {
                return Map.of();
            }
            Map<V, Double> result = new LinkedHashMap<>();
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                V member = convertMember(tuple.getValue());
                Double score = tuple.getScore();
                if (member != null && score != null) {
                    result.put(member, score);
                }
            }
            return result;
        } catch (Exception e) {
            markL2Degraded(e);
            return Map.of();
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

    private boolean setTtl(K key) {
        try {
            redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private Set<V> rangeLocal(Map<V, Double> scores, long start, long end) {
        if (scores.isEmpty()) {
            return Set.of();
        }
        List<Map.Entry<V, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator.comparing(Map.Entry<V, Double>::getValue).thenComparing(entry -> String.valueOf(entry.getKey())));
        int size = ordered.size();
        long from = start < 0 ? size + start : start;
        long to = end < 0 ? size + end : end;
        from = Math.max(0, from);
        to = Math.min(size - 1L, to);
        if (from > to || from >= size || to < 0) {
            return Set.of();
        }
        Set<V> result = new LinkedHashSet<>();
        for (int i = (int) from; i <= (int) to; i++) {
            result.add(ordered.get(i).getKey());
        }
        return result;
    }

    private V convertMember(Object raw) {
        if (raw == null) {
            return null;
        }
        if (memberType == null) {
            @SuppressWarnings("unchecked")
            V member = (V) raw;
            return member;
        }
        return memberType.isInstance(raw)
                ? memberType.cast(raw)
                : null;
    }

    private String redisKey(K key) {
        return keyspace.key(keySerializer.apply(key));
    }

    private void publishInvalidation(K key) {
        invalidationPublisher.publish(CacheInvalidationMessage.keys(
                name, java.util.List.of(keySerializer.apply(key)), instanceId));
    }

    /**
     * 广播当前 ZSet 缓存区域全部失效。
     */
    private void publishInvalidationAll() {
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    private void markL2Degraded(Exception e) {
        if (!l2Degraded) {
            l2Degraded = true;
            degradationListener.run();
            log.warn(
                    "ZSet cache [{}] L2 degraded, causeType={}",
                    name,
                    e.getClass().getSimpleName()
            );
            log.debug("ZSet cache L2 degradation detail", e);
        }
    }

    /**
     * ZSet 缓存运行统计快照。
     *
     * @param name 缓存区域名称
     * @param l1HitCount L1 命中次数
     * @param l2HitCount L2 命中次数
     * @param missCount 未命中次数
     * @param addCount 新增或更新成员计数
     * @param removeCount 删除成员计数
     * @param l1Size L1 业务 key 估算数量
     * @param l2Degraded L2 是否处于降级状态
     */
    public record Stats(String name,
                        long l1HitCount,
                        long l2HitCount,
                        long missCount,
                        long addCount,
                        long removeCount,
                        long l1Size,
                        boolean l2Degraded) {
    }
}
