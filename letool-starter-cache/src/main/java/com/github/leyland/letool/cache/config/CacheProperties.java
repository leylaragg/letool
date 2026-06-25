package com.github.leyland.letool.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存模块配置属性 —— 对应 application.yml 中 {@code letool.cache} 前缀.
 */
@ConfigurationProperties(prefix = "letool.cache")
public class CacheProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** Redis key 前缀 */
    private String redisPrefix = "letool:cache:";

    /** 缓存实例列表 */
    private List<InstanceConfig> instances = new ArrayList<>();

    /** 降级配置 */
    private Degradation degradation = new Degradation();

    /** 监控配置 */
    private Monitoring monitoring = new Monitoring();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRedisPrefix() { return redisPrefix; }
    public void setRedisPrefix(String redisPrefix) { this.redisPrefix = redisPrefix; }
    public List<InstanceConfig> getInstances() { return instances; }
    public void setInstances(List<InstanceConfig> instances) { this.instances = instances; }
    public Degradation getDegradation() { return degradation; }
    public void setDegradation(Degradation degradation) { this.degradation = degradation; }
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }

    /** 单个缓存实例配置 */
    public static class InstanceConfig {
        private String name;
        private int l1MaxSize = 2000;
        private Duration l1Ttl = Duration.ofHours(24);
        private Duration l2Ttl = Duration.ofDays(3);
        private boolean nullValueCache = true;
        private Duration nullValueTtl = Duration.ofMinutes(5);

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getL1MaxSize() { return l1MaxSize; }
        public void setL1MaxSize(int l1MaxSize) { this.l1MaxSize = l1MaxSize; }
        public Duration getL1Ttl() { return l1Ttl; }
        public void setL1Ttl(Duration l1Ttl) { this.l1Ttl = l1Ttl; }
        public Duration getL2Ttl() { return l2Ttl; }
        public void setL2Ttl(Duration l2Ttl) { this.l2Ttl = l2Ttl; }
        public boolean isNullValueCache() { return nullValueCache; }
        public void setNullValueCache(boolean nullValueCache) { this.nullValueCache = nullValueCache; }
        public Duration getNullValueTtl() { return nullValueTtl; }
        public void setNullValueTtl(Duration nullValueTtl) { this.nullValueTtl = nullValueTtl; }
    }

    /** L2 降级配置 */
    public static class Degradation {
        /** 健康检查间隔 */
        private Duration recoveryInterval = Duration.ofSeconds(30);
        /** 最大重试次数 */
        private int maxRetryCount = 3;

        public Duration getRecoveryInterval() { return recoveryInterval; }
        public void setRecoveryInterval(Duration recoveryInterval) { this.recoveryInterval = recoveryInterval; }
        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    }

    /** 监控配置 */
    public static class Monitoring {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
