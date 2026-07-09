package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 面向 Redis List 语义的二级缓存。
 *
 * <p>适合队列、时间线、事件流水等“一个 key 对应有序多个元素”的场景。L2 使用 Redis
 * List 原生结构，元素由应用配置的 RedisTemplate 序列化器处理；不会把整个 Java List
 * 作为一个 JSON value 存入 Redis。</p>
 */
public class MultiLevelListCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelListCache.class);

    private final String name;
    private final Cache<K, List<V>> l1Cache;
    private final RedisUtil redisUtil;
    private final String redisKeyPrefix;
    private final Duration l2Ttl;
    private final boolean l1Enabled;
    private final boolean l2Enabled;
    private final boolean strongConsistency;
    private final Function<K, String> keySerializer;
    private final Class<V> elementType;
    private final CacheInvalidationPublisher invalidationPublisher;
    private final String instanceId;
    private final Runnable degradationListener;

    private final AtomicLong l1HitCount = new AtomicLong();
    private final AtomicLong l2HitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong pushCount = new AtomicLong();
    private final AtomicLong popCount = new AtomicLong();

    private volatile boolean l2Degraded = false;

    MultiLevelListCache(CacheConfig<K, V> config,
                        RedisUtil redisUtil,
                        Function<K, String> keySerializer,
                        Class<V> elementType,
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
        this.elementType = elementType;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /** 从左侧推入元素。 */
    public void leftPush(K key, V value) {
        push(key, value, true);
    }

    /** 从右侧推入元素。 */
    public void rightPush(K key, V value) {
        push(key, value, false);
    }

    /** 从左侧弹出元素。 */
    public V leftPop(K key) {
        return pop(key, true);
    }

    /** 从右侧弹出元素。 */
    public V rightPop(K key) {
        return pop(key, false);
    }

    /** 获取指定范围内的元素快照，索引语义与 Redis LRANGE 一致。 */
    public List<V> range(K key, long start, long end) {
        if (key == null) {
            return Collections.emptyList();
        }
        List<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local != null && !strongConsistency) {
            l1HitCount.incrementAndGet();
            return slice(local, start, end);
        }
        if (l2Enabled && !l2Degraded) {
            List<V> l2Values = rangeFromRedis(key, start, end);
            if (!l2Values.isEmpty()) {
                l2HitCount.incrementAndGet();
                if (l1Enabled && start == 0 && end == -1) {
                    l1Cache.put(key, new ArrayList<>(l2Values));
                }
                return new ArrayList<>(l2Values);
            }
        }
        if (local != null) {
            l1HitCount.incrementAndGet();
            return slice(local, start, end);
        }
        missCount.incrementAndGet();
        return Collections.emptyList();
    }

    /** 删除整个列表。 */
    public void removeKey(K key) {
        if (key == null) {
            return;
        }
        evictLocal(key);
        deleteFromRedis(key);
        publishInvalidation(key);
    }

    void evictLocal(K key) {
        if (l1Enabled && key != null) {
            l1Cache.invalidate(key);
        }
    }

    void evictLocalAll() {
        if (l1Enabled) {
            l1Cache.invalidateAll();
        }
    }

    boolean isL2Degraded() {
        return l2Degraded;
    }

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

    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                pushCount.get(), popCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private void push(K key, V value, boolean left) {
        if (key == null || value == null) {
            return;
        }
        if (l1Enabled) {
            List<V> list = getOrCreateLocalList(key);
            if (left) {
                list.add(0, value);
            } else {
                list.add(value);
            }
        }
        pushCount.incrementAndGet();
        pushToRedis(key, value, left);
        publishInvalidation(key);
    }

    private V pop(K key, boolean left) {
        if (key == null) {
            return null;
        }
        popCount.incrementAndGet();
        V value = popFromRedis(key, left);
        if (value != null) {
            evictLocal(key);
            publishInvalidation(key);
            return value;
        }
        List<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local == null || local.isEmpty()) {
            return null;
        }
        return left ? local.remove(0) : local.remove(local.size() - 1);
    }

    private List<V> getOrCreateLocalList(K key) {
        return l1Cache.get(key, ignored -> new ArrayList<>());
    }

    private void pushToRedis(K key, V value, boolean left) {
        if (!l2Enabled || l2Degraded) {
            return;
        }
        try {
            if (left) {
                redisUtil.boundListOps(redisKey(key)).leftPush(value);
            } else {
                redisUtil.boundListOps(redisKey(key)).rightPush(value);
            }
            setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
        }
    }

    private V popFromRedis(K key, boolean left) {
        if (!l2Enabled || l2Degraded) {
            return null;
        }
        try {
            Object raw = left ? redisUtil.boundListOps(redisKey(key)).leftPop()
                    : redisUtil.boundListOps(redisKey(key)).rightPop();
            return convert(raw);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private List<V> rangeFromRedis(K key, long start, long end) {
        try {
            List<Object> raw = redisUtil.boundListOps(redisKey(key)).range(start, end);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<V> result = new ArrayList<>(raw.size());
            for (Object item : raw) {
                V value = convert(item);
                if (value != null) {
                    result.add(value);
                }
            }
            return result;
        } catch (Exception e) {
            markL2Degraded(e);
            return Collections.emptyList();
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
            log.debug("Failed to set Redis List TTL for cache [{}], key [{}]", name, key, e);
        }
    }

    private List<V> slice(List<V> source, long start, long end) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        int from = (int) Math.max(0, start);
        int to = end < 0 ? source.size() - 1 : (int) Math.min(end, source.size() - 1);
        if (from > to || from >= source.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source.subList(from, to + 1));
    }

    @SuppressWarnings("unchecked")
    private V convert(Object raw) {
        if (raw == null) {
            return null;
        }
        if (elementType == null || elementType.isInstance(raw)) {
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
            log.warn("List cache [{}] L2 degraded: {}", name, e.getMessage());
        }
    }

    public record Stats(String name,
                        long l1HitCount,
                        long l2HitCount,
                        long missCount,
                        long pushCount,
                        long popCount,
                        long l1Size,
                        boolean l2Degraded) {
    }
}
