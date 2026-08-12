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
 *
 * @param <K> 业务 key 类型
 * @param <V> List 元素类型
 */
public class MultiLevelListCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelListCache.class);

    /** 缓存区域名称，用于注册、统计、日志和失效消息路由。 */
    private final String name;
    /** L1 本地 List 缓存，key 对应一个线程安全的完整列表快照。 */
    private final Cache<K, List<V>> l1Cache;
    /** Redis 操作入口；为 {@code null} 时该缓存仅使用 L1。 */
    private final RedisUtil redisUtil;
    /** 当前 List 缓存区域的 Redis 键空间。 */
    private final RedisCacheKeyspace keyspace;
    /** Redis List 过期时间。 */
    private final Duration l2Ttl;
    /** 当前缓存实例是否启用 L1。 */
    private final boolean l1Enabled;
    /** 当前缓存实例是否启用 L2。 */
    private final boolean l2Enabled;
    /** 是否启用强一致读取。 */
    private final boolean strongConsistency;
    /** 业务 key 到 Redis key 后缀的转换函数。 */
    private final Function<K, String> keySerializer;
    /** Redis List 元素反序列化后的目标类型。 */
    private final Class<V> elementType;
    /** 跨 JVM L1 失效广播发布器。 */
    private final CacheInvalidationPublisher invalidationPublisher;
    /** 当前 JVM 缓存节点 ID。 */
    private final String instanceId;
    /** 首次进入 L2 降级时通知管理器登记恢复任务。 */
    private final Runnable degradationListener;

    /** L1 命中次数。 */
    private final AtomicLong l1HitCount = new AtomicLong();
    /** L2 命中次数。 */
    private final AtomicLong l2HitCount = new AtomicLong();
    /** L1/L2 未命中次数。 */
    private final AtomicLong missCount = new AtomicLong();
    /** 元素推入计数。 */
    private final AtomicLong pushCount = new AtomicLong();
    /** 元素弹出计数。 */
    private final AtomicLong popCount = new AtomicLong();

    /** Redis L2 当前是否处于降级状态。 */
    private volatile boolean l2Degraded = false;

    /**
     * 创建 List 二级缓存实例。
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口
     * @param keySerializer 业务 key 序列化函数
     * @param elementType Redis 元素预期类型
     * @param invalidationPublisher L1 失效广播发布器
     * @param instanceId 当前 JVM 实例标识
     * @param degradationListener 首次降级回调
     */
    MultiLevelListCache(CacheConfig<K, V> config,
                        RedisUtil redisUtil,
                        Function<K, String> keySerializer,
                        Class<V> elementType,
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
        this.elementType = resolveElementType(
                elementType,
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
     * 解析 Redis 列表元素的目标类型。
     *
     * @param explicitType 调用方显式指定的元素类型
     * @param configuredType 缓存配置声明的 value 类型
     * @param <T> 元素类型
     * @return 目标类型；均未指定时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> resolveElementType(
            Class<T> explicitType,
            Class<?> configuredType) {
        return explicitType != null
                ? explicitType
                : (Class<T>) configuredType;
    }

    /**
     * 从左侧推入元素。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param value 待推入元素；为 {@code null} 时忽略
     */
    public void leftPush(K key, V value) {
        push(key, value, true);
    }

    /**
     * 从右侧推入元素。
     *
     * @param key 业务 key；为 {@code null} 时忽略
     * @param value 待推入元素；为 {@code null} 时忽略
     */
    public void rightPush(K key, V value) {
        push(key, value, false);
    }

    /**
     * 从左侧弹出元素。
     *
     * @param key 业务 key
     * @return 弹出的元素；参数无效或列表为空时返回 {@code null}
     */
    public V leftPop(K key) {
        return pop(key, true);
    }

    /**
     * 从右侧弹出元素。
     *
     * @param key 业务 key
     * @return 弹出的元素；参数无效或列表为空时返回 {@code null}
     */
    public V rightPop(K key) {
        return pop(key, false);
    }

    /**
     * 获取指定范围内的元素快照，索引语义与 Redis LRANGE 一致。
     *
     * @param key 业务 key
     * @param start 起始索引，支持负数
     * @param end 结束索引，支持负数且包含该位置
     * @return 范围内元素快照；参数无效或无数据时返回空列表
     */
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
            if (!l2Degraded) {
                // Redis 成功返回的空列表同样是权威结果，不能回退到旧 L1。
                if (l2Values.isEmpty()) {
                    missCount.incrementAndGet();
                } else {
                    l2HitCount.incrementAndGet();
                }
                if (l1Enabled && start == 0 && end == -1) {
                    l1Cache.put(key, synchronizedSnapshot(l2Values));
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

    /**
     * 删除整个列表。
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
     * 清空当前 List 缓存区域的全部 L1/L2 数据，并广播其它 JVM 清理本地快照。
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

    /** @return 当前 List 缓存运行统计快照 */
    public Stats stats() {
        return new Stats(name, l1HitCount.get(), l2HitCount.get(), missCount.get(),
                pushCount.get(), popCount.get(), l1Enabled ? l1Cache.estimatedSize() : 0, l2Degraded);
    }

    private void push(K key, V value, boolean left) {
        if (key == null || value == null) {
            return;
        }
        List<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        // Redis 健康时，局部 push 只更新已有完整快照，不能凭单个元素创建伪完整 L1。
        boolean storedInL2 = pushToRedis(key, value, left);
        if (local != null) {
            synchronized (local) {
                if (left) {
                    local.add(0, value);
                } else {
                    local.add(value);
                }
            }
        } else if (l1Enabled && !storedInL2) {
            List<V> list = getOrCreateLocalList(key);
            if (left) {
                list.add(0, value);
            } else {
                list.add(value);
            }
        }
        pushCount.incrementAndGet();
        publishInvalidation(key);
    }

    private V pop(K key, boolean left) {
        if (key == null) {
            return null;
        }
        popCount.incrementAndGet();
        if (l2Enabled && !l2Degraded) {
            V value = popFromRedis(key, left);
            if (!l2Degraded) {
                // Redis 健康时，返回值（包括 null）都是权威结果。
                evictLocal(key);
                if (value != null) {
                    publishInvalidation(key);
                }
                return value;
            }
        }
        List<V> local = l1Enabled ? l1Cache.getIfPresent(key) : null;
        if (local == null || local.isEmpty()) {
            return null;
        }
        synchronized (local) {
            if (local.isEmpty()) {
                return null;
            }
            return left
                    ? local.remove(0)
                    : local.remove(local.size() - 1);
        }
    }

    private List<V> getOrCreateLocalList(K key) {
        return l1Cache.get(
                key,
                ignored -> Collections.synchronizedList(new ArrayList<>())
        );
    }

    private List<V> synchronizedSnapshot(List<V> source) {
        return Collections.synchronizedList(new ArrayList<>(source));
    }

    private boolean pushToRedis(K key, V value, boolean left) {
        if (!l2Enabled || l2Degraded) {
            return false;
        }
        try {
            if (left) {
                redisUtil.boundListOps(redisKey(key)).leftPush(value);
            } else {
                redisUtil.boundListOps(redisKey(key)).rightPush(value);
            }
            return setTtl(key);
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
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

    private boolean setTtl(K key) {
        try {
            redisUtil.expire(redisKey(key), l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            markL2Degraded(e);
            return false;
        }
    }

    private List<V> slice(List<V> source, long start, long end) {
        synchronized (source) {
            int size = source.size();
            if (size == 0) {
                return Collections.emptyList();
            }
            long from = start < 0 ? size + start : start;
            long to = end < 0 ? size + end : end;
            from = Math.max(0, from);
            to = Math.min(size - 1L, to);
            if (from > to || from >= size || to < 0) {
                return Collections.emptyList();
            }
            return new ArrayList<>(
                    source.subList((int) from, (int) to + 1)
            );
        }
    }

    private V convert(Object raw) {
        if (raw == null) {
            return null;
        }
        if (elementType == null) {
            @SuppressWarnings("unchecked")
            V value = (V) raw;
            return value;
        }
        if (elementType.isInstance(raw)) {
            return elementType.cast(raw);
        }
        return null;
    }

    private String redisKey(K key) {
        return keyspace.key(keySerializer.apply(key));
    }

    private void publishInvalidation(K key) {
        invalidationPublisher.publish(CacheInvalidationMessage.keys(
                name, java.util.List.of(keySerializer.apply(key)), instanceId));
    }

    /**
     * 广播当前 List 缓存区域全部失效。
     */
    private void publishInvalidationAll() {
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    private void markL2Degraded(Exception e) {
        if (!l2Degraded) {
            l2Degraded = true;
            degradationListener.run();
            log.warn(
                    "List cache [{}] L2 degraded, causeType={}",
                    name,
                    e.getClass().getSimpleName()
            );
            log.debug("List cache L2 degradation detail", e);
        }
    }

    /**
     * List 缓存运行统计快照。
     *
     * @param name 缓存区域名称
     * @param l1HitCount L1 命中次数
     * @param l2HitCount L2 命中次数
     * @param missCount 未命中次数
     * @param pushCount 推入元素计数
     * @param popCount 弹出元素计数
     * @param l1Size L1 业务 key 估算数量
     * @param l2Degraded L2 是否处于降级状态
     */
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
