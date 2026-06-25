package com.github.leyland.letool.cache.support;

/**
 * Redis Key 序列化辅助 —— 统一 Redis Key 的构建规则.
 */
public final class RedisKeySerializer {

    private RedisKeySerializer() {}

    /**
     * 构建完整的 Redis 缓存 Key.
     *
     * @param prefix    全局前缀（如 "letool:cache:"）
     * @param cacheName 缓存实例名称
     * @param key       业务键
     * @return 完整的 Redis Key（如 "letool:cache:userCache:123"）
     */
    public static String buildKey(String prefix, String cacheName, Object key) {
        return prefix + cacheName + ":" + key;
    }
}
