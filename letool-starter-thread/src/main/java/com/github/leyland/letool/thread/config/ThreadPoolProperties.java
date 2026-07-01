package com.github.leyland.letool.thread.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程池模块配置属性类，映射 {@code letool.thread} 开头的配置项。
 *
 * <p>支持定义多个命名线程池，每个线程池可独立配置核心/最大线程数、
 * 队列容量、保活时间等参数。YAML 配置示例：</p>
 * <pre>{@code
 * letool:
 *   thread:
 *     enabled: true
 *     pools:
 *       task-executor:
 *         core-pool-size: 10
 *         max-pool-size: 50
 *         queue-capacity: 500
 *         thread-name-prefix: "task-"
 *         keep-alive-seconds: 60
 *       io-executor:
 *         core-pool-size: 20
 *         max-pool-size: 200
 *         queue-capacity: 1000
 *         thread-name-prefix: "io-"
 *         virtual-threads: true
 *     monitoring:
 *       enabled: true
 *       metrics-export: true
 *     context-propagation:
 *       mdc: true
 *       security: true
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.thread")
public class ThreadPoolProperties {

    /** 是否启用线程池模块，默认 {@code true} */
    private boolean enabled = true;

    /** 线程池定义映射，key 为线程池名称，value 为配置 */
    private Map<String, PoolConfig> pools = new HashMap<>();

    /** 监控配置 */
    private Monitoring monitoring = new Monitoring();

    /** 上下文传播配置 */
    private ContextPropagation contextPropagation = new ContextPropagation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, PoolConfig> getPools() { return pools; }
    public void setPools(Map<String, PoolConfig> pools) { this.pools = pools; }
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }
    public ContextPropagation getContextPropagation() { return contextPropagation; }
    public void setContextPropagation(ContextPropagation contextPropagation) { this.contextPropagation = contextPropagation; }

    /**
     * 单个线程池的配置项。
     *
     * <p>线程池基于 {@link java.util.concurrent.ThreadPoolExecutor ThreadPoolExecutor}，
     * 任务队列使用 {@link java.util.concurrent.LinkedBlockingQueue LinkedBlockingQueue}，
     * 拒绝策略为 {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy}。</p>
     */
    public static class PoolConfig {

        /** 核心线程数，默认 5 */
        private int corePoolSize = 5;

        /** 最大线程数，默认 20 */
        private int maxPoolSize = 20;

        /** 任务队列容量（{@link java.util.concurrent.LinkedBlockingQueue}），默认 500 */
        private int queueCapacity = 500;

        /** 线程名前缀，默认 {@code "letool-"} */
        private String threadNamePrefix = "letool-";

        /** 非核心线程保活时间（秒），默认 60 */
        private int keepAliveSeconds = 60;

        /** 是否使用虚拟线程（Java 21+），默认 {@code false} */
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

    /**
     * 线程池监控配置。
     *
     * <p>启用后可通过 {@link com.github.leyland.letool.thread.monitor.ThreadPoolMonitor}
     * 获取各线程池的运行指标。</p>
     */
    public static class Monitoring {

        /** 是否启用监控，默认 {@code true} */
        private boolean enabled = true;

        /** 是否导出指标到 Micrometer，默认 {@code true} */
        private boolean metricsExport = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isMetricsExport() { return metricsExport; }
        public void setMetricsExport(boolean metricsExport) { this.metricsExport = metricsExport; }
    }

    /**
     * 上下文传播配置，控制父线程上下文向子线程的传递行为。
     *
     * <p>通过 {@link com.github.leyland.letool.thread.propagation.MdcTaskDecorator MdcTaskDecorator}
     * 和 {@link com.github.leyland.letool.thread.propagation.ContextPropagateExecutor ContextPropagateExecutor}
     * 实现跨线程上下文的自动传递。</p>
     */
    public static class ContextPropagation {

        /** 是否传递 MDC 日志上下文（如 TraceId），默认 {@code true} */
        private boolean mdc = true;

        /** 是否传递安全上下文（如当前用户信息），默认 {@code true} */
        private boolean security = true;

        public boolean isMdc() { return mdc; }
        public void setMdc(boolean mdc) { this.mdc = mdc; }
        public boolean isSecurity() { return security; }
        public void setSecurity(boolean security) { this.security = security; }
    }
}
