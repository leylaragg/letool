package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class MultiLevelZSetCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelZSetCache.class);

    /** 缓存区域名称，用于注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 ZSet 快照，key -> member/score。 */
    private final Cache<K, Map<V, Double>> l1Cache;
    /** Redis 操作工具。为 null 时退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** Redis key 前缀，最终 Redis key = redisKeyPrefix + keySerializer.apply(key)。 */
    private final String redisKeyPrefix;
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

    private final AtomicLong l1HitCount = new AtomicLong();
    private final AtomicLong l2HitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong addCount = new AtomicLong();
    private final AtomicLong removeCount = new AtomicLong();

    private volatile boolean l2Degraded = false;

    MultiLevelZSetCache(CacheConfig<K, V> config,
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
        this.memberType = memberType;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /** 添加或更新一个 ZSet 成员的分数。 */
    public void add(K key, V member, double score) {
        if (key == null || member == null) {
            return;
        }
        if (l1Enabled) {
            getOrCreateLocalScores(key).put(member, score);
        }
        addCount.incrementAndGet();
        addToRedis(key, member, score);
        publishInvalidation(key);
    }

    /** 删除指定 ZSet 成员，并清理当前 JVM 的 L1 快照。 */
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

    /** 获取指定成员的分数；强一致模式下优先读取 Redis。 */
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
            if (score != null) {
                l2HitCount.incrementAndGet();
                if (l1Enabled) {
                    getOrCreateLocalScores(key).put(member, score);
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

    /** 获取排名范围内的成员快照；索引语义与 Redis ZRANGE 一致。 */
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
            if (!scores.isEmpty()) {
                l2HitCount.incrementAndGet();
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

    /** 删除整个业务 key 对应的 ZSet。 */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        evictLocal(key);
        deleteFromRedis(key);
        publishInvalidation(key);
    }

    /** 仅清理当前 JVM 的某个 L1 条目，供失效监听器调用。 */
    void evictLocal(K key) {
        if (l1Enabled && key != null) {
            l1Cache.invalidate(key);
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

    /** 尝试恢复 Redis L2；该方法只做轻量探测，不预热数据。 */
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

    /** 返回运行统计快照。 */
    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                addCount.get(), removeCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private Map<V, Double> getOrCreateLocalScores(K key) {
        return l1Cache.get(key, ignored -> new ConcurrentHashMap<>());
    }

    private void addToRedis(K key, V member, double score) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.boundZSetOps(redisKey(key)).add(member, score);
            setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
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
            Set<Object> rawMembers = redisUtil.boundZSetOps(redisKey(key)).range(start, end);
            if (rawMembers == null || rawMembers.isEmpty()) {
                return Map.of();
            }
            Map<V, Double> result = new LinkedHashMap<>();
            for (Object raw : rawMembers) {
                V member = convertMember(raw);
                if (member != null) {
                    Double score = redisUtil.boundZSetOps(redisKey(key)).score(member);
                    result.put(member, score == null ? 0D : score);
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

    private void setTtl(K key) {
        try {
            redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Failed to set Redis ZSet TTL for cache [{}], key [{}]", name, key, e);
        }
    }

    private Set<V> rangeLocal(Map<V, Double> scores, long start, long end) {
        if (scores.isEmpty()) {
            return Set.of();
        }
        List<Map.Entry<V, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator.comparing(Map.Entry<V, Double>::getValue).thenComparing(entry -> String.valueOf(entry.getKey())));
        int from = (int) Math.max(0, start);
        int to = end < 0 ? ordered.size() - 1 : (int) Math.min(end, ordered.size() - 1);
        if (from > to || from >= ordered.size()) {
            return Set.of();
        }
        Set<V> result = new LinkedHashSet<>();
        for (int i = from; i <= to; i++) {
            result.add(ordered.get(i).getKey());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private V convertMember(Object raw) {
        if (raw == null) {
            return null;
        }
        if (memberType == null || memberType.isInstance(raw)) {
            return (V) raw;
        }
        return (V) raw;
    }

    private String redisKey(K key) {
        return redisKeyPrefix + keySerializer.apply(key);
    }

    private void publishInvalidation(K key) {
        invalidationPublisher.publish(CacheInvalidationMessage.keys(
                name, java.util.List.of(keySerializer.apply(key)), instanceId));
    }

    private void markL2Degraded(Exception e) {
        if (!l2Degraded) {
            l2Degraded = true;
            degradationListener.run();
            log.warn("ZSet cache [{}] L2 degraded: {}", name, e.getMessage());
        }
    }

    /** ZSet 缓存运行统计快照。 */
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
