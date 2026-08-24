package io.github.leylaragg.letool.cache.config;

import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.consistency.CacheReadValidation;
import io.github.leylaragg.letool.cache.consistency.CacheWritePolicy;
import io.github.leylaragg.letool.cache.core.CacheReadFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存 starter 的配置属性，对应 application.yml 中的 {@code letool.cache}。
 *
 * <p>配置分为两层：</p>
 * <ul>
 *     <li>全局配置：控制整个 starter 是否启用、全局 L1/L2 开关、Redis 前缀、强一致默认策略。</li>
 *     <li>实例配置：通过 {@link InstanceConfig} 为某个缓存区域单独设置容量、TTL 和一致性策略。</li>
 * </ul>
 *
 * <p>全局 L1/L2 开关优先级更高。比如全局关闭 L2 后，即使某个实例配置 l2Enabled=true，
 * 最终创建出来的缓存仍然不会访问 Redis。</p>
 */
@ConfigurationProperties(prefix = "letool.cache")
public class CacheProperties {

    /** 缓存 starter 总开关。 */
    private boolean enabled = true;
    /** 全局 Redis key 前缀。 */
    private String redisPrefix = "letool:cache:";
    /** 全局 L1 开关。 */
    private boolean l1Enabled = true;
    /** 全局 L2 开关。 */
    private boolean l2Enabled = true;
    /** 数据库一致性与缓存读写策略。 */
    private Consistency consistency = new Consistency();
    /** 启动时预注册的缓存实例列表。 */
    private List<InstanceConfig> instances = new ArrayList<>();
    /** Redis 降级和恢复相关配置。 */
    private Degradation degradation = new Degradation();
    /** 监控 Bean 配置。 */
    private Monitoring monitoring = new Monitoring();
    /** 注解 AOP 配置。 */
    private AnnotationConfig annotation = new AnnotationConfig();
    /** 跨 JVM L1 失效广播配置。 */
    private Invalidation invalidation = new Invalidation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRedisPrefix() { return redisPrefix; }
    public void setRedisPrefix(String redisPrefix) { this.redisPrefix = redisPrefix; }
    public boolean isL1Enabled() { return l1Enabled; }
    public void setL1Enabled(boolean l1Enabled) { this.l1Enabled = l1Enabled; }
    public boolean isL2Enabled() { return l2Enabled; }
    public void setL2Enabled(boolean l2Enabled) { this.l2Enabled = l2Enabled; }
    public boolean isStrongConsistency() { return consistency.getReadValidation() == CacheReadValidation.VERSIONED; }
    public void setStrongConsistency(boolean strongConsistency) {
        consistency.setReadValidation(strongConsistency ? CacheReadValidation.VERSIONED : CacheReadValidation.NONE);
    }
    public Consistency getConsistency() { return consistency; }
    public void setConsistency(Consistency consistency) { this.consistency = consistency; }
    public List<InstanceConfig> getInstances() { return instances; }
    public void setInstances(List<InstanceConfig> instances) { this.instances = instances; }
    public Degradation getDegradation() { return degradation; }
    public void setDegradation(Degradation degradation) { this.degradation = degradation; }
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }
    public AnnotationConfig getAnnotation() { return annotation; }
    public void setAnnotation(AnnotationConfig annotation) { this.annotation = annotation; }
    public Invalidation getInvalidation() { return invalidation; }
    public void setInvalidation(Invalidation invalidation) { this.invalidation = invalidation; }

    /**
     * 数据库一致性与缓存读写策略配置。
     */
    public static class Consistency {
        /** 数据库修改协调模式。 */
        private CacheConsistencyMode mode = CacheConsistencyMode.TRANSACTIONAL;
        /** L1 命中时的读取校验策略。 */
        private CacheReadValidation readValidation = CacheReadValidation.VERSIONED;
        /** 数据库修改成功后的缓存处理策略。 */
        private CacheWritePolicy writePolicy = CacheWritePolicy.INVALIDATE;
        /** Redis 读取失败后的默认处理策略。 */
        private CacheReadFailurePolicy readFailurePolicy =
                CacheReadFailurePolicy.STALE_IF_AVAILABLE;
        /** DURABLE Redis 围栏最大存活时间。 */
        private Duration fenceTtl = Duration.ofMinutes(2);
        /** Outbox 恢复扫描间隔。 */
        private Duration recoveryInterval = Duration.ofSeconds(5);
        /** Outbox 单批领取数量。 */
        private int recoveryBatchSize = 100;
        /** Outbox 处理租约。 */
        private Duration recoveryLease = Duration.ofSeconds(30);
        /** Outbox 失败后的基础重试延迟。 */
        private Duration retryBaseDelay = Duration.ofSeconds(1);
        /** 单 Key 版本元数据保留期。 */
        private Duration versionMetadataRetention = Duration.ofDays(7);
        /** JDBC Outbox 表名。 */
        private String outboxTable = "letool_cache_outbox";
        /** 已完成 Outbox 事件保留时间。 */
        private Duration completedRetention = Duration.ofDays(7);
        /** 已完成 Outbox 事件清理间隔。 */
        private Duration cleanupInterval = Duration.ofHours(1);
        /** 已完成 Outbox 事件单次清理数量。 */
        private int cleanupBatchSize = 1000;

        public CacheConsistencyMode getMode() { return mode; }
        public void setMode(CacheConsistencyMode mode) { this.mode = mode; }
        public CacheReadValidation getReadValidation() { return readValidation; }
        public void setReadValidation(CacheReadValidation readValidation) { this.readValidation = readValidation; }
        public CacheWritePolicy getWritePolicy() { return writePolicy; }
        public void setWritePolicy(CacheWritePolicy writePolicy) { this.writePolicy = writePolicy; }
        public CacheReadFailurePolicy getReadFailurePolicy() { return readFailurePolicy; }
        public void setReadFailurePolicy(CacheReadFailurePolicy readFailurePolicy) { this.readFailurePolicy = readFailurePolicy; }
        public Duration getFenceTtl() { return fenceTtl; }
        public void setFenceTtl(Duration fenceTtl) { this.fenceTtl = fenceTtl; }
        public Duration getRecoveryInterval() { return recoveryInterval; }
        public void setRecoveryInterval(Duration recoveryInterval) { this.recoveryInterval = recoveryInterval; }
        public int getRecoveryBatchSize() { return recoveryBatchSize; }
        public void setRecoveryBatchSize(int recoveryBatchSize) { this.recoveryBatchSize = recoveryBatchSize; }
        public Duration getRecoveryLease() { return recoveryLease; }
        public void setRecoveryLease(Duration recoveryLease) { this.recoveryLease = recoveryLease; }
        public Duration getRetryBaseDelay() { return retryBaseDelay; }
        public void setRetryBaseDelay(Duration retryBaseDelay) { this.retryBaseDelay = retryBaseDelay; }
        public Duration getVersionMetadataRetention() { return versionMetadataRetention; }
        public void setVersionMetadataRetention(Duration versionMetadataRetention) { this.versionMetadataRetention = versionMetadataRetention; }
        public String getOutboxTable() { return outboxTable; }
        public void setOutboxTable(String outboxTable) { this.outboxTable = outboxTable; }
        public Duration getCompletedRetention() { return completedRetention; }
        public void setCompletedRetention(Duration completedRetention) { this.completedRetention = completedRetention; }
        public Duration getCleanupInterval() { return cleanupInterval; }
        public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
        public int getCleanupBatchSize() { return cleanupBatchSize; }
        public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    }

    /**
     * 单个缓存区域的配置项。
     */
    public static class InstanceConfig {
        /** 缓存区域名称。 */
        private String name;
        /** L1 最大条目数。 */
        private int l1MaxSize = 2000;
        /** 单次 Redis pipeline 的最大业务 Key 数。 */
        private int redisBatchSize = 256;
        /** L1 TTL。 */
        private Duration l1Ttl = Duration.ofHours(24);
        /** L2 TTL。 */
        private Duration l2Ttl = Duration.ofDays(3);
        /** 当前缓存区域是否启用 L1。 */
        private boolean l1Enabled = true;
        /** 当前缓存区域是否启用 L2。 */
        private boolean l2Enabled = true;
        /** 当前缓存区域是否启用强一致版本校验。 */
        private boolean strongConsistency = true;
        /** 当前缓存区域的一致性模式；为空时继承全局配置。 */
        private CacheConsistencyMode consistencyMode;
        /** 当前缓存区域的读取校验策略；为空时继承全局配置。 */
        private CacheReadValidation readValidation;
        /** 当前缓存区域的写策略；为空时继承全局配置。 */
        private CacheWritePolicy writePolicy;
        /** Redis 读取失败策略；为空时继承全局配置。 */
        private CacheReadFailurePolicy readFailurePolicy;
        /** 单 Key 版本元数据保留期；为空时继承全局配置。 */
        private Duration versionMetadataRetention;
        /** 是否缓存 null 结果。 */
        private boolean nullValueCache = true;
        /** null 哨兵 TTL。 */
        private Duration nullValueTtl = Duration.ofMinutes(5);

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getL1MaxSize() { return l1MaxSize; }
        public void setL1MaxSize(int l1MaxSize) { this.l1MaxSize = l1MaxSize; }
        public int getRedisBatchSize() { return redisBatchSize; }
        public void setRedisBatchSize(int redisBatchSize) { this.redisBatchSize = redisBatchSize; }
        public Duration getL1Ttl() { return l1Ttl; }
        public void setL1Ttl(Duration l1Ttl) { this.l1Ttl = l1Ttl; }
        public Duration getL2Ttl() { return l2Ttl; }
        public void setL2Ttl(Duration l2Ttl) { this.l2Ttl = l2Ttl; }
        public boolean isL1Enabled() { return l1Enabled; }
        public void setL1Enabled(boolean l1Enabled) { this.l1Enabled = l1Enabled; }
        public boolean isL2Enabled() { return l2Enabled; }
        public void setL2Enabled(boolean l2Enabled) { this.l2Enabled = l2Enabled; }
        public boolean isStrongConsistency() { return strongConsistency; }
        public void setStrongConsistency(boolean strongConsistency) { this.strongConsistency = strongConsistency; }
        public CacheConsistencyMode getConsistencyMode() { return consistencyMode; }
        public void setConsistencyMode(CacheConsistencyMode consistencyMode) { this.consistencyMode = consistencyMode; }
        public CacheReadValidation getReadValidation() { return readValidation; }
        public void setReadValidation(CacheReadValidation readValidation) { this.readValidation = readValidation; }
        public CacheWritePolicy getWritePolicy() { return writePolicy; }
        public void setWritePolicy(CacheWritePolicy writePolicy) { this.writePolicy = writePolicy; }
        public CacheReadFailurePolicy getReadFailurePolicy() { return readFailurePolicy; }
        public void setReadFailurePolicy(CacheReadFailurePolicy readFailurePolicy) { this.readFailurePolicy = readFailurePolicy; }
        public Duration getVersionMetadataRetention() { return versionMetadataRetention; }
        public void setVersionMetadataRetention(Duration versionMetadataRetention) { this.versionMetadataRetention = versionMetadataRetention; }
        public boolean isNullValueCache() { return nullValueCache; }
        public void setNullValueCache(boolean nullValueCache) { this.nullValueCache = nullValueCache; }
        public Duration getNullValueTtl() { return nullValueTtl; }
        public void setNullValueTtl(Duration nullValueTtl) { this.nullValueTtl = nullValueTtl; }
    }

    /**
     * Redis L2 降级恢复配置。
     */
    public static class Degradation {
        /** 是否启用后台恢复探测。 */
        private boolean recoveryEnabled = true;
        /** 恢复探测间隔。 */
        private Duration recoveryInterval = Duration.ofSeconds(30);
        /** 预留字段：最大重试次数。当前恢复逻辑按间隔持续探测。 */
        private int maxRetryCount = 3;

        public boolean isRecoveryEnabled() { return recoveryEnabled; }
        public void setRecoveryEnabled(boolean recoveryEnabled) { this.recoveryEnabled = recoveryEnabled; }
        public Duration getRecoveryInterval() { return recoveryInterval; }
        public void setRecoveryInterval(Duration recoveryInterval) { this.recoveryInterval = recoveryInterval; }
        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    }

    /**
     * 缓存监控配置。
     */
    public static class Monitoring {
        /** 是否注册 CacheMonitor Bean。 */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 注解式缓存配置。
     */
    public static class AnnotationConfig {
        /** 是否启用 @MultiLevelCacheable 等注解的 AOP 切面。 */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 跨 JVM L1 失效广播配置。
     */
    public static class Invalidation {
        /** 是否启用 Redis pub/sub 失效广播。 */
        private boolean enabled = true;
        /** Redis pub/sub 频道名称。 */
        private String channel = "letool:cache:invalidation";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
    }
}
