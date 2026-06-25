package com.github.leyland.letool.cache.core;

import java.time.Duration;
import java.util.function.Function;

/**
 * 二级缓存接口 —— L1（Caffeine 本地）+ L2（Redis 分布式），支持读穿/写穿.
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public interface MultiLevelCache<K, V> {

    /**
     * 读穿模式 —— 先查 L1，未命中查 L2，仍未命中调用 loader 加载并回填.
     *
     * @param key    缓存键
     * @param loader 数据加载器（如从数据库查询）
     * @return 缓存值，可能为 null（如果 loader 返回 null）
     */
    V getOrLoad(K key, Function<K, V> loader);

    /**
     * 写入缓存（使用默认 TTL）.
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 写入缓存（使用自定义 TTL，覆盖默认配置）.
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     */
    void put(K key, V value, Duration ttl);

    /**
     * 删除缓存.
     *
     * @param key 缓存键
     */
    void evict(K key);

    /**
     * 获取缓存统计信息.
     *
     * @return 统计快照
     */
    CacheStats stats();

    /**
     * 获取缓存实例名称.
     *
     * @return 名称（来自 {@link CacheConfig#getName()}）
     */
    String getName();
}
