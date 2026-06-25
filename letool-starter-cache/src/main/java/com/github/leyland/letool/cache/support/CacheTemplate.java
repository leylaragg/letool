package com.github.leyland.letool.cache.support;

import com.github.leyland.letool.cache.core.MultiLevelCache;

import java.time.Duration;
import java.util.function.Function;

/**
 * 缓存操作模板 —— 简化常用的缓存操作.
 *
 * <pre>{@code
 * // 读穿
 * User user = cacheTemplate.getOrLoad(cache, "user:123", key -> userMapper.selectById(123));
 *
 * // 写入
 * cacheTemplate.put(cache, "user:123", user, Duration.ofHours(1));
 *
 * // 删除
 * cacheTemplate.evict(cache, "user:123");
 * }</pre>
 */
public final class CacheTemplate {

    private CacheTemplate() {}

    /**
     * 读穿模式获取缓存.
     *
     * @param cache  缓存实例
     * @param key    缓存键
     * @param loader 数据加载器
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存值
     */
    public static <K, V> V getOrLoad(MultiLevelCache<K, V> cache, K key, Function<K, V> loader) {
        return cache.getOrLoad(key, loader);
    }

    /**
     * 写入缓存（使用默认 TTL）.
     */
    public static <K, V> void put(MultiLevelCache<K, V> cache, K key, V value) {
        cache.put(key, value);
    }

    /**
     * 写入缓存（指定 TTL）.
     */
    public static <K, V> void put(MultiLevelCache<K, V> cache, K key, V value, Duration ttl) {
        cache.put(key, value, ttl);
    }

    /**
     * 删除缓存.
     */
    public static <K, V> void evict(MultiLevelCache<K, V> cache, K key) {
        cache.evict(key);
    }
}
