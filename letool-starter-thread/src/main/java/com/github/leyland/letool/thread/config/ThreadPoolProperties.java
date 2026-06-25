package com.github.leyland.letool.thread.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "letool.thread")
public class ThreadPoolProperties {

    private boolean enabled = true;
    private Map<String, PoolConfig> pools = new HashMap<>();
    private Monitoring monitoring = new Monitoring();
    private ContextPropagation contextPropagation = new ContextPropagation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, PoolConfig> getPools() { return pools; }
    public void setPools(Map<String, PoolConfig> pools) { this.pools = pools; }
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }
    public ContextPropagation getContextPropagation() { return contextPropagation; }
    public void setContextPropagation(ContextPropagation contextPropagation) { this.contextPropagation = contextPropagation; }

    public static class PoolConfig {
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        private int queueCapacity = 500;
        private String threadNamePrefix = "letool-";
        private int keepAliveSeconds = 60;
        private boolean virtualThreads = false;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
        public boolean isVirtualThreads() { return virtualThreads; }
        public void setVirtualThreads(boolean virtualThreads) { this.virtualThreads = virtualThreads; }
    }

    public static class Monitoring {
        private boolean enabled = true;
        private boolean metricsExport = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isMetricsExport() { return metricsExport; }
        public void setMetricsExport(boolean metricsExport) { this.metricsExport = metricsExport; }
    }

    public static class ContextPropagation {
        private boolean mdc = true;
        private boolean security = true;

        public boolean isMdc() { return mdc; }
        public void setMdc(boolean mdc) { this.mdc = mdc; }
        public boolean isSecurity() { return security; }
        public void setSecurity(boolean security) { this.security = security; }
    }
}
