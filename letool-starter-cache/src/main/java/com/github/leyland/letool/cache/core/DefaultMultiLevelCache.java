package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 二级缓存默认实现.
 *
 * <h3>读路径</h3>
 * <pre>
 * get(key) → L1 命中 → 返回
 *          → L1 未命中 → L2 命中 → 回填 L1 → 返回
 *                      → L2 未命中 → 调用 loader → 写入 L2 → 写入 L1 → 返回
 *                      → loader 返回 null + nullValueCache → 写入 NULL_SENTINEL → 返回 null
 * </pre>
 *
 * <h3>L2 降级</h3>
 * <p>当 Redis 不可达时自动降级：跳过所有 Redis 操作，L1 未命中直接调 loader.
 * 每 30 秒通过 PING 探测恢复.</p>
 */
public class DefaultMultiLevelCache<K, V> implements MultiLevelCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(DefaultMultiLevelCache.class);

    private final String name;
    private final Cache<K, Object> l1Cache;
    private final RedisUtil redisUtil;
    private final CacheSerializer serializer;
    private final CacheConfig<K, V> config;
    private final CacheStats stats = new CacheStats();
    private final ScheduledExecutorService degradationScheduler;

    private volatile boolean l2Degraded = false;

    @SuppressWarnings("unchecked")
    public DefaultMultiLevelCache(CacheConfig<K, V> config, RedisUtil redisUtil, CacheSerializer serializer) {
        this.name = config.getName();
        this.redisUtil = redisUtil;
        this.serializer = serializer;
        this.config = config;

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .scheduler(Scheduler.systemScheduler());

        if (config.isNullValueCache()) {
            builder.executor(Runnable::run);
        }

        this.l1Cache = (Cache<K, Object>) builder.build();
        this.degradationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-degrade-" + name);
            t.setDaemon(true);
            return t;
        });

        if (redisUtil != null) {
            degradationScheduler.scheduleWithFixedDelay(
                    this::checkL2Health, 30, 30, TimeUnit.SECONDS);
        }
    }

    // ======================== 读路径 ========================

    @Override
    public V getOrLoad(K key, Function<K, V> loader) {
        // 1. 查 L1
        Object l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            stats.recordL1Hit();
            return unwrapNull(l1Value);
        }

        // 2. 查 L2 (Redis) —— 未降级时
        if (redisUtil != null && !l2Degraded) {
            V l2Value = getFromL2(key);
            if (l2Value != null) {
                stats.recordL2Hit();
                l1Cache.put(key, wrapValue(l2Value));
                return l2Value;
            }
            // L2 中命中 NULL_SENTINEL
            if (isL2NullSentinel(key)) {
                stats.recordL2Hit();
                l1Cache.put(key, NullSentinel.INSTANCE);
                return null;
            }
        }

        // 3. 未命中 → 调用 loader
        stats.recordMiss();
        stats.recordLoad();
        try {
            V loaded = loader.apply(key);
            if (loaded != null) {
                stats.recordLoadSuccess();
                // 回填 L1 + L2
                l1Cache.put(key, loaded);
                putToL2(key, loaded, config.getL2Ttl());
            } else if (config.isNullValueCache()) {
                stats.recordLoadSuccess();
                l1Cache.put(key, NullSentinel.INSTANCE);
                putNullSentinelToL2(key);
            } else {
                stats.recordLoadSuccess();
            }
            return loaded;
        } catch (Exception e) {
            stats.recordLoadFailure();
            throw new CacheException("Failed to load cache value for key: " + key + " in cache: " + name, e);
        }
    }

    // ======================== 写路径 ========================

    @Override
    public void put(K key, V value) {
        put(key, value, config.getL1Ttl());
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        l1Cache.put(key, wrapValue(value));
        putToL2(key, value, ttl);
    }

    // ======================== 删除 ========================

    @Override
    public void evict(K key) {
        l1Cache.invalidate(key);
        stats.recordEviction();
        if (redisUtil != null && !l2Degraded) {
            try {
                redisUtil.delete(buildRedisKey(key));
            } catch (Exception e) {
                log.warn("Failed to evict L2 key [{}] in cache [{}]", key, name, e);
            }
        }
    }

    // ======================== 统计 ========================

    @Override
    public CacheStats stats() {
        return stats;
    }

    @Override
    public String getName() {
        return name;
    }

    // ======================== L2 操作 ========================

    @SuppressWarnings("unchecked")
    private V getFromL2(K key) {
        if (redisUtil == null || l2Degraded) return null;
        try {
            String json = redisUtil.get(buildRedisKey(key));
            if (json == null || json.isEmpty()) return null;
            if (NullSentinel.INSTANCE.toString().equals(json)) return null;
            return (V) serializer.deserialize(json, Object.class);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private boolean isL2NullSentinel(K key) {
        if (redisUtil == null || l2Degraded) return false;
        try {
            String json = redisUtil.get(buildRedisKey(key));
            return NullSentinel.INSTANCE.toString().equals(json);
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private void putToL2(K key, V value, Duration ttl) {
        if (redisUtil == null || l2Degraded) return;
        try {
            String json = serializer.serialize(value);
            redisUtil.set(buildRedisKey(key), json, ttl);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private void putNullSentinelToL2(K key) {
        if (redisUtil == null || l2Degraded) return;
        try {
            redisUtil.set(buildRedisKey(key), NullSentinel.INSTANCE.toString(), config.getNullValueTtl());
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    // ======================== 降级 / 恢复 ========================

    private void markL2Degraded(Exception cause) {
        if (!l2Degraded) {
            l2Degraded = true;
            stats.recordL2Degraded();
            log.warn("L2 cache [{}] degraded due to Redis error: {}", name, cause.getMessage());
        }
    }

    private void checkL2Health() {
        if (!l2Degraded || redisUtil == null) return;
        try {
            String healthKey = config.getRedisKeyPrefix() + name + ":__health_check__";
            redisUtil.hasKey(healthKey);
            l2Degraded = false;
            log.info("L2 cache [{}] recovered", name);
        } catch (Exception ignored) {
            // still degraded
        }
    }

    // ======================== 内部工具方法 ========================

    private String buildRedisKey(K key) {
        return config.getRedisKeyPrefix() + name + ":" + key;
    }

    private Object wrapValue(V value) {
        return value == null ? NullSentinel.INSTANCE : value;
    }

    @SuppressWarnings("unchecked")
    private V unwrapNull(Object value) {
        if (value instanceof NullSentinel) return null;
        return (V) value;
    }
}
