package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.consistency.CacheReadValidation;
import io.github.leylaragg.letool.cache.consistency.CacheWritePolicy;
import io.github.leylaragg.letool.cache.exception.CacheException;

import java.time.Duration;
import java.lang.reflect.Type;
import java.util.function.Function;

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

    /** 未经过管理器合并时使用的兼容 Redis Key 前缀。 */
    public static final String DEFAULT_REDIS_KEY_PREFIX = "letool:cache:";

    /** 缓存区域名称，会参与 CacheManager 注册和 Redis key 拼接。 */
    private final String name;
    /** L1 Caffeine 最大条目数。 */
    private int l1MaxSize = 2000;
    /** 单次 Redis pipeline 包含的最大业务 Key 数。 */
    private int redisBatchSize = 256;
    /** 是否启用 L1 本地缓存。 */
    private boolean l1Enabled = true;
    /** L1 写入后的过期时间。 */
    private Duration l1Ttl = Duration.ofHours(24);
    /** L2 Redis 写入后的过期时间。 */
    private Duration l2Ttl = Duration.ofDays(3);
    /** 单 Key 版本元数据的保留时间。 */
    private Duration versionMetadataRetention = Duration.ofDays(7);
    /** DURABLE 写围栏最长存活时间，用于计算版本元数据安全窗口。 */
    private Duration fenceTtl = Duration.ofMinutes(2);
    /** 一致性恢复扫描间隔，用于计算版本元数据安全窗口。 */
    private Duration recoveryInterval = Duration.ofSeconds(5);
    /** 是否启用 L2 Redis 缓存。 */
    private boolean l2Enabled = true;
    /** 数据库修改与缓存失效之间的一致性模式。 */
    private CacheConsistencyMode consistencyMode = CacheConsistencyMode.TRANSACTIONAL;
    /** L1 命中时采用的读取校验策略。 */
    private CacheReadValidation readValidation = CacheReadValidation.VERSIONED;
    /** 数据库修改成功后的缓存处理策略。 */
    private CacheWritePolicy writePolicy = CacheWritePolicy.INVALIDATE;
    /** Redis 权威读取失败时的返回策略。 */
    private CacheReadFailurePolicy readFailurePolicy = CacheReadFailurePolicy.STALE_IF_AVAILABLE;
    /** 是否缓存 loader 返回的 null，防止不存在的数据频繁穿透到数据库。 */
    private boolean nullValueCache = true;
    /** null 哨兵 TTL，通常应短于正常业务值 TTL。 */
    private Duration nullValueTtl = Duration.ofMinutes(5);
    /** Redis Key 前缀；最终 Redis Key 还会拼接缓存名称和业务 Key。 */
    private String redisKeyPrefix = DEFAULT_REDIS_KEY_PREFIX;
    /** 调用方是否显式设置过单缓存前缀，用于区别默认展示值和覆盖全局配置的真实意图。 */
    private boolean redisKeyPrefixExplicitlySet;
    /** L2 读取时用于校验 RedisTemplate 反序列化结果的 value 类型；为 null 时跳过严格类型校验。 */
    private Type valueType;
    /** 业务 key 的稳定字符串序列化器，参与 Redis Key、版本、围栏和失效广播。 */
    private Function<K, String> keySerializer = String::valueOf;

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
     * 设置是否启用版本读取校验。
     *
     * <p>该方法只为兼容旧配置保留，不会开启数据库持久化一致性模式。</p>
     *
     * @param strongConsistency {@code true} 表示读取时执行一致性校验
     * @return 当前配置对象
     */
    public CacheConfig<K, V> strongConsistency(boolean strongConsistency) {
        this.readValidation = strongConsistency ? CacheReadValidation.VERSIONED : CacheReadValidation.NONE;
        return this;
    }

    /**
     * 设置数据库一致性模式。
     *
     * @param consistencyMode 数据库一致性模式
     * @return 当前配置对象
     */
    public CacheConfig<K, V> consistencyMode(CacheConsistencyMode consistencyMode) {
        this.consistencyMode = consistencyMode;
        return this;
    }

    /**
     * 设置 L1 读取校验策略。
     *
     * @param readValidation 读取校验策略
     * @return 当前配置对象
     */
    public CacheConfig<K, V> readValidation(CacheReadValidation readValidation) {
        this.readValidation = readValidation;
        return this;
    }

    /**
     * 设置数据库修改成功后的缓存处理策略。
     *
     * @param writePolicy 缓存写策略
     * @return 当前配置对象
     */
    public CacheConfig<K, V> writePolicy(CacheWritePolicy writePolicy) {
        this.writePolicy = writePolicy;
        return this;
    }

    /** @param retention 单 Key 版本元数据保留期 @return 当前配置对象 */
    public CacheConfig<K, V> versionMetadataRetention(Duration retention) {
        this.versionMetadataRetention = retention;
        return this;
    }

    /** @param fenceTtl DURABLE 写围栏最长存活时间 @return 当前配置对象 */
    public CacheConfig<K, V> fenceTtl(Duration fenceTtl) {
        this.fenceTtl = fenceTtl;
        return this;
    }

    /** @param recoveryInterval 一致性恢复扫描间隔 @return 当前配置对象 */
    public CacheConfig<K, V> recoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
        return this;
    }

    /**
     * 设置批量 Redis 操作的分块大小。
     *
     * @param redisBatchSize 单批业务 Key 数，必须大于零
     * @return 当前配置对象
     */
    public CacheConfig<K, V> redisBatchSize(int redisBatchSize) {
        this.redisBatchSize = redisBatchSize;
        return this;
    }

    /**
     * 设置 Redis 权威读取失败时的返回策略。
     *
     * @param readFailurePolicy Redis 读取失败策略
     * @return 当前配置对象
     */
    public CacheConfig<K, V> readFailurePolicy(CacheReadFailurePolicy readFailurePolicy) {
        this.readFailurePolicy = readFailurePolicy;
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
        this.redisKeyPrefixExplicitlySet = true;
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
     * 设置包含泛型参数的缓存 value 类型。
     *
     * @param valueType 类或参数化类型
     * @return 当前配置对象
     */
    public CacheConfig<K, V> valueType(Type valueType) {
        this.valueType = valueType;
        return this;
    }

    /**
     * 设置业务 key 的稳定字符串序列化器。
     *
     * <p>复合对象 key 必须显式配置确定性的序列化规则，不能依赖默认 {@code toString()} 的实现细节。</p>
     *
     * @param keySerializer key 序列化器
     * @return 当前配置对象
     */
    public CacheConfig<K, V> keySerializer(Function<K, String> keySerializer) {
        this.keySerializer = keySerializer;
        return this;
    }

    /**
     * 将业务 key 转换为框架内部统一使用的稳定字符串。
     *
     * @param key 业务 key
     * @return 非空的稳定字符串
     */
    public String serializeKey(K key) {
        String serializedKey = keySerializer.apply(key);
        if (serializedKey == null || serializedKey.isBlank()) {
            throw CacheException.configurationInvalid("serialized-key");
        }
        return serializedKey;
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
        if (versionMetadataRetention == null || versionMetadataRetention.isZero()
                || versionMetadataRetention.isNegative()
                || fenceTtl == null || fenceTtl.isNegative()
                || recoveryInterval == null || recoveryInterval.isNegative()) {
            throw CacheException.configurationInvalid("version-metadata-retention");
        }
        Duration metadataSafetyWindow = l1Ttl
                .plus(fenceTtl.compareTo(recoveryInterval) >= 0
                        ? fenceTtl : recoveryInterval)
                .plus(Duration.ofMinutes(10));
        if (versionMetadataRetention.compareTo(metadataSafetyWindow) < 0) {
            throw CacheException.configurationInvalid("version-metadata-retention");
        }
        if (redisKeyPrefix == null || redisKeyPrefix.trim().isEmpty()) {
            throw CacheException.configurationInvalid("redis-key-prefix");
        }
        if (nullValueTtl == null || nullValueTtl.isZero() || nullValueTtl.isNegative()) {
            throw CacheException.configurationInvalid("null-value-ttl");
        }
        if (consistencyMode == null) {
            throw CacheException.configurationInvalid("consistency-mode");
        }
        if (readValidation == null) {
            throw CacheException.configurationInvalid("read-validation");
        }
        if (writePolicy == null) {
            throw CacheException.configurationInvalid("write-policy");
        }
        if (redisBatchSize <= 0) {
            throw CacheException.configurationInvalid("redis-batch-size");
        }
        if (readFailurePolicy == null) {
            throw CacheException.configurationInvalid("read-failure-policy");
        }
        if (keySerializer == null) {
            throw CacheException.configurationInvalid("key-serializer");
        }
        if (consistencyMode == CacheConsistencyMode.DURABLE
                && (!l2Enabled || readValidation != CacheReadValidation.VERSIONED)) {
            throw CacheException.configurationInvalid("durable-requires-l2-versioned");
        }
        return this;
    }

    /** @return 缓存区域名称 */
    public String getName() { return name; }

    /** @return L1 最大条目数 */
    public int getL1MaxSize() { return l1MaxSize; }

    /** @return 单次 Redis pipeline 的最大业务 Key 数 */
    public int getRedisBatchSize() { return redisBatchSize; }

    /** @return 当前缓存区域是否启用 L1 */
    public boolean isL1Enabled() { return l1Enabled; }

    /** @return L1 写入后的过期时间 */
    public Duration getL1Ttl() { return l1Ttl; }

    /** @return L2 Redis 业务值的过期时间 */
    public Duration getL2Ttl() { return l2Ttl; }

    /** @return 单 Key 版本元数据保留期 */
    public Duration getVersionMetadataRetention() { return versionMetadataRetention; }

    /** @return DURABLE 写围栏最长存活时间 */
    public Duration getFenceTtl() { return fenceTtl; }

    /** @return 一致性恢复扫描间隔 */
    public Duration getRecoveryInterval() { return recoveryInterval; }

    /** @return 当前缓存区域是否启用 L2 */
    public boolean isL2Enabled() { return l2Enabled; }

    /** @return 是否启用版本读取校验；仅为兼容旧 API 保留 */
    public boolean isStrongConsistency() { return readValidation == CacheReadValidation.VERSIONED; }

    /** @return 数据库一致性模式 */
    public CacheConsistencyMode getConsistencyMode() { return consistencyMode; }

    /** @return L1 读取校验策略 */
    public CacheReadValidation getReadValidation() { return readValidation; }

    /** @return 数据库修改成功后的缓存处理策略 */
    public CacheWritePolicy getWritePolicy() { return writePolicy; }

    /** @return Redis 权威读取失败时的返回策略 */
    public CacheReadFailurePolicy getReadFailurePolicy() { return readFailurePolicy; }

    /** @return 是否缓存 null 哨兵 */
    public boolean isNullValueCache() { return nullValueCache; }

    /** @return null 哨兵过期时间 */
    public Duration getNullValueTtl() { return nullValueTtl; }

    /** @return Redis key 前缀 */
    public String getRedisKeyPrefix() { return redisKeyPrefix; }

    /**
     * 判断调用方是否明确覆盖了管理器全局 Redis Key 前缀。
     *
     * @return {@code true} 表示单缓存前缀由调用方显式设置
     */
    boolean hasExplicitRedisKeyPrefix() { return redisKeyPrefixExplicitlySet; }

    /** @return Redis 反序列化结果的预期 value 类型 */
    public Class<?> getValueType() {
        return valueType instanceof Class<?> clazz ? clazz : null;
    }

    /** @return 完整 value 类型描述，包括参数化类型 */
    public Type getValueTypeDescriptor() { return valueType; }
}
