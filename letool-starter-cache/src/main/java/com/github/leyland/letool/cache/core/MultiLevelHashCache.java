package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 面向 Redis Hash 语义的二级缓存。
 *
 * <p>适合“一组字段挂在同一个业务 key 下”的场景，例如用户资料字段、配置项字段、统计维度字段。
 * L2 使用 Redis Hash 原生结构，每个 field/value 都交给应用配置的 RedisTemplate 序列化器处理。</p>
 */
public class MultiLevelHashCache<K, HK, HV> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelHashCache.class);

    /** 缓存区域名称，用于注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 Hash 缓存，key -> field/value 快照。 */
    private final Cache<K, Map<HK, HV>> l1Cache;
    /** Redis 操作工具。为 null 时退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** Redis key 前缀，最终 Redis key = redisKeyPrefix + keySerializer.apply(key)。 */
    private final String redisKeyPrefix;
    /** Redis Hash 的过期时间，每次写入后都会补充 TTL。 */
    private final Duration l2Ttl;
    /** 当前缓存实例是否启用 L1。 */
    private final boolean l1Enabled;
    /** 当前缓存实例是否启用 L2。 */
    private final boolean l2Enabled;
    /** 是否启用强一致读取；开启后读取优先走 Redis。 */
    private final boolean strongConsistency;
    /** 业务 key 到 Redis key 后缀的转换函数。 */
    private final Function<K, String> keySerializer;
    /** Hash field 读取后的目标类型。 */
    private final Class<HK> hashKeyType;
    /** Hash value 读取后的目标类型。 */
    private final Class<HV> hashValueType;
    /** 跨 JVM L1 失效广播发布器。 */
    private final CacheInvalidationPublisher invalidationPublisher;
    /** 当前 JVM 缓存节点 ID，用于忽略自己发出的失效广播。 */
    private final String instanceId;
    /** 首次 Redis 异常进入降级时，通知 CacheManager 记录待恢复缓存。 */
    private final Runnable degradationListener;

    private final AtomicLong l1HitCount = new AtomicLong();
    private final AtomicLong l2HitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong deleteCount = new AtomicLong();

    private volatile boolean l2Degraded = false;

    MultiLevelHashCache(CacheConfig<K, HV> config,
                        RedisUtil redisUtil,
                        Function<K, String> keySerializer,
                        Class<HK> hashKeyType,
                        Class<HV> hashValueType,
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
        this.hashKeyType = hashKeyType;
        this.hashValueType = hashValueType;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /**
     * 写入或覆盖一个 Hash 字段。
     *
     * <p>字段和值会直接交给 RedisTemplate 的 hashKey/hashValue 序列化器处理，不做字符串化。</p>
     */
    public void put(K key, HK field, HV value) {
        if (key == null || field == null || value == null) {
            return;
        }
        if (l1Enabled) {
            getOrCreateLocalMap(key).put(field, value);
        }
        putCount.incrementAndGet();
        putToRedis(key, field, value);
        publishInvalidation(key);
    }

    /** 批量写入 Hash 字段；null field/value 会被忽略。 */
    public void putAll(K key, Map<HK, HV> values) {
        if (key == null || values == null || values.isEmpty()) {
            return;
        }
        Map<HK, HV> filtered = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            if (field != null && value != null) {
                filtered.put(field, value);
            }
        });
        if (filtered.isEmpty()) {
            return;
        }
        if (l1Enabled) {
            getOrCreateLocalMap(key).putAll(filtered);
        }
        putCount.addAndGet(filtered.size());
        putAllToRedis(key, filtered);
        publishInvalidation(key);
    }

    /** 读取指定 Hash 字段；强一致模式下优先读取 Redis。 */
    public HV get(K key, HK field) {
        if (key == null || field == null) {
            return null;
        }
        Map<HK, HV> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency && local.containsKey(field)) {
            l1HitCount.incrementAndGet();
            return local.get(field);
        }
        if (l2Enabled && !l2Degraded) {
            HV value = getFromRedis(key, field);
            if (value != null) {
                l2HitCount.incrementAndGet();
                if (l1Enabled) {
                    getOrCreateLocalMap(key).put(field, value);
                }
                return value;
            }
        }
        if (local != null && local.containsKey(field)) {
            l1HitCount.incrementAndGet();
            return local.get(field);
        }
        missCount.incrementAndGet();
        return null;
    }

    /** 读取整个 Hash 的字段快照；返回值是新的 Map，调用方修改不会污染缓存内部状态。 */
    public Map<HK, HV> entries(K key) {
        if (key == null) {
            return Map.of();
        }
        Map<HK, HV> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return new LinkedHashMap<>(local);
        }
        if (l2Enabled && !l2Degraded) {
            Map<HK, HV> values = entriesFromRedis(key);
            if (!values.isEmpty()) {
                l2HitCount.incrementAndGet();
                if (l1Enabled) {
                    l1Cache.put(key, new ConcurrentHashMap<>(values));
                }
                return new LinkedHashMap<>(values);
            }
        }
        if (local != null) {
            l1HitCount.incrementAndGet();
            return new LinkedHashMap<>(local);
        }
        missCount.incrementAndGet();
        return Map.of();
    }

    /** 删除指定 Hash 字段，并清理当前 JVM 的 L1 快照。 */
    public void delete(K key, HK field) {
        if (key == null || field == null) {
            return;
        }
        if (l1Enabled) {
            Map<HK, HV> local = l1Cache.getIfPresent(key);
            if (local != null) {
                local.remove(field);
            }
        }
        deleteCount.incrementAndGet();
        deleteFromRedis(key, field);
        publishInvalidation(key);
    }

    /** 删除整个业务 key 对应的 Hash。 */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        evictLocal(key);
        deleteKeyFromRedis(key);
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
                putCount.get(), deleteCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private Map<HK, HV> getOrCreateLocalMap(K key) {
        return l1Cache.get(key, ignored -> new ConcurrentHashMap<>());
    }

    private void putToRedis(K key, HK field, HV value) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.boundHashOps(redisKey(key)).put(field, value);
            setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void putAllToRedis(K key, Map<HK, HV> values) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.boundHashOps(redisKey(key)).putAll(values);
            setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private HV getFromRedis(K key, HK field) {
        try {
            Object raw = redisUtil.boundHashOps(redisKey(key)).get(field);
            return convertValue(raw);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Map<HK, HV> entriesFromRedis(K key) {
        try {
            Map<Object, Object> raw = redisUtil.boundHashOps(redisKey(key)).entries();
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<HK, HV> result = new LinkedHashMap<>();
            raw.forEach((field, value) -> {
                HK convertedField = convertField(field);
                HV convertedValue = convertValue(value);
                if (convertedField != null && convertedValue != null) {
                    result.put(convertedField, convertedValue);
                }
            });
            return result;
        } catch (Exception e) {
            markL2Degraded(e);
            return Map.of();
        }
    }

    private void deleteFromRedis(K key, HK field) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            redisUtil.boundHashOps(redisKey(key)).delete(field);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void deleteKeyFromRedis(K key) {
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
            log.debug("Failed to set Redis Hash TTL for cache [{}], key [{}]", name, key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private HK convertField(Object raw) {
        if (raw == null) {
            return null;
        }
        if (hashKeyType == null || hashKeyType.isInstance(raw)) {
            return (HK) raw;
        }
        return (HK) raw;
    }

    @SuppressWarnings("unchecked")
    private HV convertValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (hashValueType == null || hashValueType.isInstance(raw)) {
            return (HV) raw;
        }
        return (HV) raw;
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
            log.warn("Hash cache [{}] L2 degraded: {}", name, e.getMessage());
        }
    }

    /** Hash 缓存运行统计快照。 */
    public record Stats(String name,
                        long l1HitCount,
                        long l2HitCount,
                        long missCount,
                        long putCount,
                        long deleteCount,
                        long l1Size,
                        boolean l2Degraded) {
    }
}
