package com.github.leyland.letool.cache.core;

import java.time.Duration;

/**
 * 缓存实例配置 —— Builder 模式构建.
 *
 * <pre>{@code
 * CacheConfig<String, User> config = CacheConfig.<String, User>builder("userCache")
 *     .l1MaxSize(2000)
 *     .l1Ttl(Duration.ofHours(24))
 *     .l2Ttl(Duration.ofDays(3))
 *     .build();
 * }</pre>
 */
public class CacheConfig<K, V> {

    /** 缓存实例名称（用于 Redis key 前缀和日志标识） */
    private final String name;

    /** L1 最大容量 */
    private int l1MaxSize = 2000;

    /** L1 过期时间 */
    private Duration l1Ttl = Duration.ofHours(24);

    /** L2 过期时间 */
    private Duration l2Ttl = Duration.ofDays(3);

    /** 是否缓存 null 值（防穿透） */
    private boolean nullValueCache = true;

    /** null 值哨兵过期时间 */
    private Duration nullValueTtl = Duration.ofMinutes(5);

    /** Redis key 前缀 */
    private String redisKeyPrefix = "letool:cache:";

    private CacheConfig(String name) {
        this.name = name;
    }

    public static <K, V> CacheConfig<K, V> builder(String name) {
        return new CacheConfig<>(name);
    }

    public CacheConfig<K, V> l1MaxSize(int l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
        return this;
    }

    public CacheConfig<K, V> l1Ttl(Duration l1Ttl) {
        this.l1Ttl = l1Ttl;
        return this;
    }

    public CacheConfig<K, V> l2Ttl(Duration l2Ttl) {
        this.l2Ttl = l2Ttl;
        return this;
    }

    public CacheConfig<K, V> nullValueCache(boolean nullValueCache) {
        this.nullValueCache = nullValueCache;
        return this;
    }

    public CacheConfig<K, V> nullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
        return this;
    }

    public CacheConfig<K, V> redisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
        return this;
    }

    public CacheConfig<K, V> build() {
        return this;
    }

    // ---- getters ----

    public String getName() { return name; }
    public int getL1MaxSize() { return l1MaxSize; }
    public Duration getL1Ttl() { return l1Ttl; }
    public Duration getL2Ttl() { return l2Ttl; }
    public boolean isNullValueCache() { return nullValueCache; }
    public Duration getNullValueTtl() { return nullValueTtl; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
}
