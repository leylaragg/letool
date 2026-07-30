package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.exception.CacheException;

import java.time.Duration;

/**
 * 单个缓存区域的配置模型。
 *
 * <p>一个 {@code CacheConfig} 对应一个独立缓存区域，例如 {@code user:byId}、{@code rule:byCode}。
 * 不同缓存区域应该使用不同名称，避免 Redis key、L1 本地副本和统计信息混用。</p>
 *
 * <p>TTL 设计约束：</p>
 * <ul>
 *     <li>L1 TTL 表示本地 Caffeine 缓存的最长存活时间。</li>
 *     <li>L2 TTL 表示 Redis 中业务值的存活时间。</li>
 *     <li>L2 TTL 必须大于等于 L1 TTL，避免 Redis 已过期但本地仍返回旧值。</li>
 *     <li>从 Redis 回填 L1 时，实际 L1 TTL 还会取 Redis 剩余 TTL 和配置 L1 TTL 的较小值。</li>
 * </ul>
 *
 * <p>强一致模式默认开启。开启后，L1 命中前会读取 Redis 缓存区域版本，只有本地版本和 Redis 版本一致时才返回 L1。</p>
 */
public class CacheConfig<K, V> {

    /** 缓存区域名称，会参与 CacheManager 注册和 Redis key 拼接。 */
    private final String name;
    /** L1 Caffeine 最大条目数。 */
    private int l1MaxSize = 2000;
    /** 是否启用 L1 本地缓存。 */
    private boolean l1Enabled = true;
    /** L1 写入后的过期时间。 */
    private Duration l1Ttl = Duration.ofHours(24);
    /** L2 Redis 写入后的过期时间。 */
    private Duration l2Ttl = Duration.ofDays(3);
    /** 是否启用 L2 Redis 缓存。 */
    private boolean l2Enabled = true;
    /** 是否启用 Redis 版本校验，避免跨 JVM 返回旧 L1 数据。 */
    private boolean strongConsistency = true;
    /** 是否缓存 loader 返回的 null，防止不存在的数据频繁穿透到数据库。 */
    private boolean nullValueCache = true;
    /** null 哨兵 TTL，通常应短于正常业务值 TTL。 */
    private Duration nullValueTtl = Duration.ofMinutes(5);
    /** Redis key 前缀；最终 KV Redis key 还会拼接缓存名称和业务 key。 */
    private String redisKeyPrefix = "letool:cache:";
    /** L2 读取时用于校验 RedisTemplate 反序列化结果的 value 类型；为 null 时跳过严格类型校验。 */
    private Class<?> valueType;

    private CacheConfig(String name) {
        this.name = name;
    }

    /**
     * 创建缓存配置构建器。
     *
     * @param name 缓存区域名称，不能为空
     * @param <K> 业务 key 类型
     * @param <V> 缓存 value 类型
     * @return 可继续链式配置的缓存配置对象
     */
    public static <K, V> CacheConfig<K, V> builder(String name) {
        return new CacheConfig<>(name);
    }

    /**
     * 设置 L1 最大条目数。
     *
     * @param l1MaxSize L1 最大条目数，必须大于零
     * @return 当前配置对象
     */
    public CacheConfig<K, V> l1MaxSize(int l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
        return this;
    }

    /**
     * 设置当前缓存区域是否启用 L1。
     *
     * @param l1Enabled {@code true} 表示启用 L1
     * @return 当前配置对象
     */
    public CacheConfig<K, V> l1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
        return this;
    }

    /**
     * 设置 L1 TTL。
     *
     * <p>如果当前 L2 TTL 比新的 L1 TTL 短，会自动把 L2 TTL 拉齐到 L1 TTL，减少配置顺序带来的误用。
     * 最终 build 时仍会校验 L2 TTL 不能短于 L1 TTL。</p>
     *
     * @param l1Ttl L1 写入后的过期时间
     * @return 当前配置对象
     */
    public CacheConfig<K, V> l1Ttl(Duration l1Ttl) {
        this.l1Ttl = l1Ttl;
        if (this.l2Ttl != null && l1Ttl != null && this.l2Ttl.compareTo(l1Ttl) < 0) {
            this.l2Ttl = l1Ttl;
        }
        return this;
    }

    /**
     * 设置 L2 TTL。
     *
     * @param l2Ttl Redis 业务值的过期时间
     * @return 当前配置对象
     */
    public CacheConfig<K, V> l2Ttl(Duration l2Ttl) {
        this.l2Ttl = l2Ttl;
        return this;
    }

    /**
     * 设置当前缓存区域是否启用 L2。
     *
     * @param l2Enabled {@code true} 表示启用 L2
     * @return 当前配置对象
     */
    public CacheConfig<K, V> l2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
        return this;
    }

    /**
     * 设置是否启用强一致版本校验。
     *
     * @param strongConsistency {@code true} 表示读取时执行一致性校验
     * @return 当前配置对象
     */
    public CacheConfig<K, V> strongConsistency(boolean strongConsistency) {
        this.strongConsistency = strongConsistency;
        return this;
    }

    /**
     * 设置是否缓存 null 值。
     *
     * @param nullValueCache {@code true} 表示缓存 null 哨兵
     * @return 当前配置对象
     */
    public CacheConfig<K, V> nullValueCache(boolean nullValueCache) {
        this.nullValueCache = nullValueCache;
        return this;
    }

    /**
     * 设置 null 哨兵 TTL。
     *
     * @param nullValueTtl null 哨兵过期时间
     * @return 当前配置对象
     */
    public CacheConfig<K, V> nullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
        return this;
    }

    /**
     * 设置 Redis key 前缀。
     *
     * @param redisKeyPrefix Redis key 前缀
     * @return 当前配置对象
     */
    public CacheConfig<K, V> redisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
        return this;
    }

    /**
     * 设置缓存 value 类型，用于 L2 命中时校验 Redis 反序列化结果。
     *
     * <p>不设置时保持兼容行为；设置后，如果 Redis 中的值不是该类型，L2 会按 miss 处理并回源。</p>
     *
     * @param valueType Redis 反序列化结果的预期类型
     * @return 当前配置对象
     */
    public CacheConfig<K, V> valueType(Class<?> valueType) {
        this.valueType = valueType;
        return this;
    }

    /**
     * 完成配置构建并执行基础校验。
     *
     * @return 校验通过的当前配置对象
     * @throws CacheException 配置字段不合法时抛出
     */
    public CacheConfig<K, V> build() {
        if (name == null || name.trim().isEmpty()) {
            throw CacheException.configurationInvalid("name");
        }
        if (l1MaxSize <= 0) {
            throw CacheException.configurationInvalid("l1-max-size");
        }
        if (l1Ttl == null || l1Ttl.isZero() || l1Ttl.isNegative()) {
            throw CacheException.configurationInvalid("l1-ttl");
        }
        if (l2Ttl == null || l2Ttl.isZero() || l2Ttl.isNegative()) {
            throw CacheException.configurationInvalid("l2-ttl");
        }
        if (l2Ttl.compareTo(l1Ttl) < 0) {
            throw CacheException.configurationInvalid("l2-ttl");
        }
        if (redisKeyPrefix == null || redisKeyPrefix.trim().isEmpty()) {
            throw CacheException.configurationInvalid("redis-key-prefix");
        }
        if (nullValueTtl == null || nullValueTtl.isZero() || nullValueTtl.isNegative()) {
            throw CacheException.configurationInvalid("null-value-ttl");
        }
        return this;
    }

    /** @return 缓存区域名称 */
    public String getName() { return name; }

    /** @return L1 最大条目数 */
    public int getL1MaxSize() { return l1MaxSize; }

    /** @return 当前缓存区域是否启用 L1 */
    public boolean isL1Enabled() { return l1Enabled; }

    /** @return L1 写入后的过期时间 */
    public Duration getL1Ttl() { return l1Ttl; }

    /** @return L2 Redis 业务值的过期时间 */
    public Duration getL2Ttl() { return l2Ttl; }

    /** @return 当前缓存区域是否启用 L2 */
    public boolean isL2Enabled() { return l2Enabled; }

    /** @return 是否启用强一致校验 */
    public boolean isStrongConsistency() { return strongConsistency; }

    /** @return 是否缓存 null 哨兵 */
    public boolean isNullValueCache() { return nullValueCache; }

    /** @return null 哨兵过期时间 */
    public Duration getNullValueTtl() { return nullValueTtl; }

    /** @return Redis key 前缀 */
    public String getRedisKeyPrefix() { return redisKeyPrefix; }

    /** @return Redis 反序列化结果的预期 value 类型 */
    public Class<?> getValueType() { return valueType; }
}
