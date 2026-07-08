package com.github.leyland.letool.cache.config;

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
    /** 全局强一致开关。 */
    private boolean strongConsistency = true;
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
    public boolean isStrongConsistency() { return strongConsistency; }
    public void setStrongConsistency(boolean strongConsistency) { this.strongConsistency = strongConsistency; }
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
     * 单个缓存区域的配置项。
     */
    public static class InstanceConfig {
        /** 缓存区域名称。 */
        private String name;
        /** L1 最大条目数。 */
        private int l1MaxSize = 2000;
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
        /** 是否缓存 null 结果。 */
        private boolean nullValueCache = true;
        /** null 哨兵 TTL。 */
        private Duration nullValueTtl = Duration.ofMinutes(5);

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getL1MaxSize() { return l1MaxSize; }
        public void setL1MaxSize(int l1MaxSize) { this.l1MaxSize = l1MaxSize; }
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
