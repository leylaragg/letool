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
 *
 * @param <K> 业务 key 类型
 * @param <HK> Hash field 类型
 * @param <HV> Hash value 类型
 */
public class MultiLevelHashCache<K, HK, HV> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelHashCache.class);

    /** 缓存区域名称，用于注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 Hash 缓存，key -> field/value 快照。 */
    private final Cache<K, Map<HK, HV>> l1Cache;
    /** Redis 操作工具。为 null 时退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** 当前 Hash 缓存区域的 Redis 键空间。 */
    private final RedisCacheKeyspace keyspace;
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

    /** L1 命中次数。 */
    private final AtomicLong l1HitCount = new AtomicLong();
    /** L2 命中次数。 */
    private final AtomicLong l2HitCount = new AtomicLong();
    /** L1/L2 未命中次数。 */
    private final AtomicLong missCount = new AtomicLong();
    /** 写入字段计数。 */
    private final AtomicLong putCount = new AtomicLong();
    /** 删除字段计数。 */
    private final AtomicLong deleteCount = new AtomicLong();

    /** Redis L2 当前是否处于降级状态。 */
    private volatile boolean l2Degraded = false;

    /**
     * 创建 Hash 二级缓存实例。
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口
     * @param keySerializer 业务 key 序列化函数
     * @param hashKeyType Redis Hash field 预期类型
     * @param hashValueType Redis Hash value 预期类型
     * @param invalidationPublisher L1 失效广播发布器
     * @param instanceId 当前 JVM 实例标识
     * @param degradationListener 首次降级回调
     */
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
        this.keyspace = new RedisCacheKeyspace(config.getRedisKeyPrefix(), name);
        this.l2Ttl = config.getL2Ttl();
        this.l1Enabled = config.isL1Enabled();
        this.l2Enabled = redisUtil != null && config.isL2Enabled();
        this.strongConsistency = config.isStrongConsistency();
        this.keySerializer = keySerializer == null ? String::valueOf : keySerializer;
        this.hashKeyType = hashKeyType;
        this.hashValueType = resolveHashValueType(
                hashValueType,
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
     * 解析 Redis Hash value 的目标类型。
     *
     * @param explicitType 调用方显式指定的 value 类型
     * @param configuredType 缓存配置声明的 value 类型
     * @param <T> Hash value 类型
     * @return 目标类型；均未指定时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> resolveHashValueType(
            Class<T> explicitType,
            Class<?> configuredType) {
        return explicitType != null
                ? explicitType
                : (Class<T>) configuredType;
    }

    /**
     * 写入或覆盖一个 Hash 字段。
     *
     * <p>字段和值会直接交给 RedisTemplate 的 hashKey/hashValue 序列化器处理，不做字符串化。</p>
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param field Hash field；为 {@code null} 时忽略
     * @param value Hash value；为 {@code null} 时忽略
     */
    public void put(K key, HK field, HV value) {
        if (key == null || field == null || value == null) {
            return;
        }
        putCount.incrementAndGet();
        Map<HK, HV> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        // Redis 健康时，局部 put 只更新已有完整快照，不能凭单个字段创建伪完整 L1。
        boolean storedInL2 = putToRedis(key, field, value);
        if (local != null) {
            local.put(field, value);
        } else if (l1Enabled && !storedInL2) {
            getOrCreateLocalMap(key).put(field, value);
        }
        publishInvalidation(key);
    }

    /**
     * 批量写入 Hash 字段；null field/value 会被忽略。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param values 待写入的 field/value 映射
     */
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
        putCount.addAndGet(filtered.size());
        Map<HK, HV> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        boolean storedInL2 = putAllToRedis(key, filtered);
        if (local != null) {
            local.putAll(filtered);
        } else if (l1Enabled && !storedInL2) {
            getOrCreateLocalMap(key).putAll(filtered);
        }
        publishInvalidation(key);
    }

    /**
     * 读取指定 Hash 字段；强一致模式下优先读取 Redis。
     *
     * @param key 业务 key
     * @param field Hash field
     * @return 字段值；参数无效或字段不存在时返回 {@code null}
     */
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
            if (!l2Degraded) {
                if (value != null) {
                    l2HitCount.incrementAndGet();
                } else {
                    missCount.incrementAndGet();
                }
                if (local != null) {
                    if (value == null) {
                        local.remove(field);
                    } else {
                        local.put(field, value);
                    }
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

    /**
     * 读取整个 Hash 的字段快照；返回值是新的 Map，调用方修改不会污染缓存内部状态。
     *
     * @param key 业务 key
     * @return 完整字段快照；参数无效或无数据时返回空 Map
     */
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
            if (!l2Degraded) {
                // Redis 成功返回的空 Hash 同样是权威结果，不能回退到旧 L1。
                if (values.isEmpty()) {
                    missCount.incrementAndGet();
                } else {
                    l2HitCount.incrementAndGet();
                }
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

    /**
     * 删除指定 Hash 字段，并清理当前 JVM 的 L1 快照。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param field 待删除 field；为 {@code null} 时忽略
     */
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

    /**
     * 删除整个业务 key 对应的 Hash。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        evictLocal(key);
        deleteKeyFromRedis(key);
        publishInvalidation(key);
    }

    /**
     * 清空当前 Hash 缓存区域的全部 L1/L2 数据，并广播其它 JVM 清理本地快照。
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

    /** @return 当前 Hash 缓存运行统计快照 */
    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                putCount.get(), deleteCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private Map<HK, HV> getOrCreateLocalMap(K key) {
        return l1Cache.get(key, ignored -> new ConcurrentHashMap<>());
    }

    private boolean putToRedis(K key, HK field, HV value) {
        if (!l2Enabled || l2Degraded) {
            return false;
        }
        try {
            redisUtil.boundHashOps(redisKey(key)).put(field, value);
            return setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private boolean putAllToRedis(K key, Map<HK, HV> values) {
        if (!l2Enabled || l2Degraded) {
            return false;
        }
        try {
            redisUtil.boundHashOps(redisKey(key)).putAll(values);
            return setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
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

    private boolean setTtl(K key) {
        try {
            redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private HK convertField(Object raw) {
        if (raw == null) {
            return null;
        }
        if (hashKeyType == null) {
            @SuppressWarnings("unchecked")
            HK field = (HK) raw;
            return field;
        }
        return hashKeyType.isInstance(raw)
                ? hashKeyType.cast(raw)
                : null;
    }

    private HV convertValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (hashValueType == null) {
            @SuppressWarnings("unchecked")
            HV value = (HV) raw;
            return value;
        }
        return hashValueType.isInstance(raw)
                ? hashValueType.cast(raw)
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
     * 广播当前 Hash 缓存区域全部失效。
     */
    private void publishInvalidationAll() {
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    private void markL2Degraded(Exception e) {
        if (!l2Degraded) {
            l2Degraded = true;
            degradationListener.run();
            log.warn(
                    "Hash cache [{}] L2 degraded, causeType={}",
                    name,
                    e.getClass().getSimpleName()
            );
            log.debug("Hash cache L2 degradation detail", e);
        }
    }

    /**
     * Hash 缓存运行统计快照。
     *
     * @param name 缓存区域名称
     * @param l1HitCount L1 命中次数
     * @param l2HitCount L2 命中次数
     * @param missCount 未命中次数
     * @param putCount 写入字段计数
     * @param deleteCount 删除字段计数
     * @param l1Size L1 业务 key 估算数量
     * @param l2Degraded L2 是否处于降级状态
     */
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
