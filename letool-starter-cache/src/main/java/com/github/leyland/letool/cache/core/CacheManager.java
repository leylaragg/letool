package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理器 —— 注册和获取所有 {@link MultiLevelCache} 实例.
 *
 * <p>内部使用 {@link ConcurrentHashMap} 管理所有缓存实例，线程安全.</p>
 */
public class CacheManager {

    private final Map<String, MultiLevelCache<?, ?>> caches = new ConcurrentHashMap<>();
    private final RedisUtil redisUtil;
    private final CacheSerializer serializer;

    public CacheManager(RedisUtil redisUtil, CacheSerializer serializer) {
        this.redisUtil = redisUtil;
        this.serializer = serializer;
    }

    /**
     * 获取或创建缓存实例.
     *
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存实例（如果已存在则返回已有的）
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelCache<K, V> getOrCreate(CacheConfig<K, V> config) {
        return (MultiLevelCache<K, V>) caches.computeIfAbsent(config.getName(),
                name -> new DefaultMultiLevelCache<>(config, redisUtil, serializer));
    }

    /**
     * 获取已注册的缓存实例.
     *
     * @param name 缓存名称
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 缓存实例
     * @throws CacheException 如果缓存不存在
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelCache<K, V> get(String name) {
        MultiLevelCache<?, ?> cache = caches.get(name);
        if (cache == null) {
            throw new CacheException("Cache not found: " + name + ". Use getOrCreate() first.");
        }
        return (MultiLevelCache<K, V>) cache;
    }

    /**
     * 移除并销毁缓存实例.
     *
     * @param name 缓存名称
     */
    public void remove(String name) {
        caches.remove(name);
    }

    /**
     * 获取所有缓存实例.
     *
     * @return 缓存实例集合
     */
    public Collection<MultiLevelCache<?, ?>> getAll() {
        return caches.values();
    }
}
