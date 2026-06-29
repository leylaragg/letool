package com.github.leyland.letool.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * letool-starter-job 配置属性——映射 application.yml 中 letool.job 开头的配置项.
 *
 * <p>通过 {@code @ConfigurationProperties(prefix = "letool.job")} 与配置文件绑定，
 * 支持 IDEA 的配置自动补全（依赖 spring-boot-configuration-processor）.</p>
 *
 * <h3>配置示例（application.yml）</h3>
 * <pre>{@code
 * letool:
 *   job:
 *     enabled: true
 *     thread-pool-size: 8
 *     shard:
 *       enabled: true
 *       default-total: 4
 *     retry:
 *       max-retries: 5
 *       backoff-ms: 2000
 *       backoff-multiplier: 1.5
 *     log:
 *       enabled: true
 *       retention-days: 60
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobAutoConfiguration
 */
@ConfigurationProperties(prefix = "letool.job")
public class JobProperties {

    // ======================== 顶层属性 ========================

    /**
     * 是否启用任务调度功能（默认 true）.
     */
    private boolean enabled = true;

    /**
     * 任务执行线程池大小（默认 4）.
     */
    private int threadPoolSize = 4;

    /**
     * 分片配置.
     */
    private Shard shard = new Shard();

    /**
     * 重试配置.
     */
    private Retry retry = new Retry();

    /**
     * 日志配置.
     */
    private Log log = new Log();

    // ======================== 内部类 - 分片配置 ========================

    /**
     * 任务分片配置.
     */
    public static class Shard {

        /**
         * 是否启用分片功能（默认 true）.
         */
        private boolean enabled = true;

        /**
         * 默认总分片数（默认 1）.
         */
        private int defaultTotal = 1;

        /**
         * 获取是否启用分片.
         *
         * @return {@code true} 如果启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用分片.
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取默认总分片数.
         *
         * @return 默认总分片数
         */
        public int getDefaultTotal() {
            return defaultTotal;
        }

        /**
         * 设置默认总分片数.
         *
         * @param defaultTotal 默认总分片数
         */
        public void setDefaultTotal(int defaultTotal) {
            this.defaultTotal = defaultTotal;
        }
    }

    // ======================== 内部类 - 重试配置 ========================

    /**
     * 失败重试配置.
     */
    public static class Retry {

        /**
         * 最大重试次数（默认 3）.
         */
        private int maxRetries = 3;

        /**
         * 退避基础时间（毫秒，默认 1000）.
         */
        private long backoffMs = 1000;

        /**
         * 退避倍率（默认 2.0，即指数退避）.
         */
        private double backoffMultiplier = 2.0;

        /**
         * 获取最大重试次数.
         *
         * @return 最大重试次数
         */
        public int getMaxRetries() {
            return maxRetries;
        }

        /**
         * 设置最大重试次数.
         *
         * @param maxRetries 最大重试次数
         */
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        /**
         * 获取退避基础时间（毫秒）.
         *
         * @return 退避基础时间
         */
        public long getBackoffMs() {
            return backoffMs;
        }

        /**
         * 设置退避基础时间（毫秒）.
         *
         * @param backoffMs 退避基础时间
         */
        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }

        /**
         * 获取退避倍率.
         *
         * @return 退避倍率
         */
        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        /**
         * 设置退避倍率.
         *
         * @param backoffMultiplier 退避倍率
         */
        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    // ======================== 内部类 - 日志配置 ========================

    /**
     * 任务日志配置.
     */
    public static class Log {

        /**
         * 是否启用日志功能（默认 true）.
         */
        private boolean enabled = true;

        /**
         * 日志保留天数（默认 30）.
         */
        private int retentionDays = 30;

        /**
         * 获取是否启用日志.
         *
         * @return {@code true} 如果启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用日志.
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取日志保留天数.
         *
         * @return 保留天数
         */
        public int getRetentionDays() {
            return retentionDays;
        }

        /**
         * 设置日志保留天数.
         *
         * @param retentionDays 保留天数
         */
        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }

    // ======================== 顶层 Getter/Setter ========================

    /**
     * 获取是否启用任务调度.
     *
     * @return {@code true} 如果启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用任务调度.
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取线程池大小.
     *
     * @return 线程池大小
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * 设置线程池大小.
     *
     * @param threadPoolSize 线程池大小
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * 获取分片配置.
     *
     * @return 分片配置
     */
    public Shard getShard() {
        return shard;
    }

    /**
     * 设置分片配置.
     *
     * @param shard 分片配置
     */
    public void setShard(Shard shard) {
        this.shard = shard;
    }

    /**
     * 获取重试配置.
     *
     * @return 重试配置
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * 设置重试配置.
     *
     * @param retry 重试配置
     */
    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /**
     * 获取日志配置.
     *
     * @return 日志配置
     */
    public Log getLog() {
        return log;
    }

    /**
     * 设置日志配置.
     *
     * @param log 日志配置
     */
    public void setLog(Log log) {
        this.log = log;
    }
}
