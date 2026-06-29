package com.github.leyland.letool.job.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务定义——描述一个任务的所有静态配置信息.
 *
 * <p>每个需要调度的任务必须通过 {@code JobDefinition} 定义其元数据，
 * 包括任务名、Cron 表达式、分片配置、重试策略以及实际执行的 {@link JobHandler}.
 * 使用 Builder 模式构建实例.</p>
 *
 * <h3>典型配置示例</h3>
 * <pre>{@code
 * JobDefinition job = JobDefinition.builder()
 *         .jobName("dailyReportJob")
 *         .cron("0 0 6 * * ?")
 *         .description("每日报表生成任务")
 *         .shardTotal(4)
 *         .maxRetries(3)
 *         .backoffMs(1000)
 *         .backoffMultiplier(2.0)
 *         .handler(context -> reportService.generateDailyReport(context))
 *         .param("reportType", "daily")
 *         .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobHandler
 * @see JobScheduler
 */
public class JobDefinition {

    // ======================== 成员变量 ========================

    /**
     * 任务名称（全局唯一标识）.
     */
    private final String jobName;

    /**
     * Cron 表达式（支持标准7位 cron 表达式）.
     */
    private final String cron;

    /**
     * 任务描述文本.
     */
    private final String description;

    /**
     * 总分片数（默认1，表示不分片）.
     */
    private final int shardTotal;

    /**
     * 分片索引（默认0，通常由调度器动态分配）.
     */
    private final int shardIndex;

    /**
     * 最大重试次数（默认3）.
     */
    private final int maxRetries;

    /**
     * 重试基础退避时间（毫秒，默认1000）.
     */
    private final long backoffMs;

    /**
     * 退避倍率（默认2.0，即指数退避）.
     */
    private final double backoffMultiplier;

    /**
     * 任务处理器（执行核心逻辑）.
     */
    private final JobHandler handler;

    /**
     * 自定义参数（不可变）.
     */
    private final Map<String, Object> params;

    // ======================== 私有构造方法 ========================

    private JobDefinition(Builder builder) {
        this.jobName = builder.jobName;
        this.cron = builder.cron;
        this.description = builder.description;
        this.shardTotal = builder.shardTotal > 0 ? builder.shardTotal : 1;
        this.shardIndex = builder.shardIndex;
        this.maxRetries = builder.maxRetries;
        this.backoffMs = builder.backoffMs;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.handler = builder.handler;
        this.params = Collections.unmodifiableMap(new HashMap<>(builder.params));
    }

    // ======================== Getter 方法 ========================

    /**
     * 获取任务名称.
     *
     * @return 任务名称
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 获取 Cron 表达式.
     *
     * @return Cron 表达式
     */
    public String getCron() {
        return cron;
    }

    /**
     * 获取任务描述.
     *
     * @return 任务描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取总分片数.
     *
     * @return 总分片数
     */
    public int getShardTotal() {
        return shardTotal;
    }

    /**
     * 获取分片索引.
     *
     * @return 分片索引
     */
    public int getShardIndex() {
        return shardIndex;
    }

    /**
     * 获取最大重试次数.
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取重试基础退避时间（毫秒）.
     *
     * @return 基础退避毫秒数
     */
    public long getBackoffMs() {
        return backoffMs;
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
     * 获取任务处理器.
     *
     * @return 任务处理器
     */
    public JobHandler getHandler() {
        return handler;
    }

    /**
     * 获取自定义参数（不可变Map）.
     *
     * @return 自定义参数Map
     */
    public Map<String, Object> getParams() {
        return params;
    }

    // ======================== Builder ========================

    /**
     * 创建 Builder 实例.
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link JobDefinition} 的建造者.
     *
     * <p>提供流式 API 构建 JobDefinition 实例. 默认值如下：</p>
     * <ul>
     *   <li>shardTotal: 1</li>
     *   <li>shardIndex: 0</li>
     *   <li>maxRetries: 3</li>
     *   <li>backoffMs: 1000</li>
     *   <li>backoffMultiplier: 2.0</li>
     * </ul>
     */
    public static class Builder {
        private String jobName;
        private String cron;
        private String description = "";
        private int shardTotal = 1;
        private int shardIndex = 0;
        private int maxRetries = 3;
        private long backoffMs = 1000;
        private double backoffMultiplier = 2.0;
        private JobHandler handler;
        private final Map<String, Object> params = new HashMap<>();

        /**
         * 设置任务名称（必填）.
         *
         * @param jobName 任务名称（全局唯一）
         * @return 当前 Builder
         */
        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        /**
         * 设置 Cron 表达式.
         *
         * @param cron Cron 表达式（如 "0 0 6 * * ?"）
         * @return 当前 Builder
         */
        public Builder cron(String cron) {
            this.cron = cron;
            return this;
        }

        /**
         * 设置任务描述.
         *
         * @param description 任务描述文本
         * @return 当前 Builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置总分片数.
         *
         * @param shardTotal 总分片数（&gt;=1）
         * @return 当前 Builder
         */
        public Builder shardTotal(int shardTotal) {
            this.shardTotal = shardTotal;
            return this;
        }

        /**
         * 设置分片索引.
         *
         * @param shardIndex 分片索引（从0开始）
         * @return 当前 Builder
         */
        public Builder shardIndex(int shardIndex) {
            this.shardIndex = shardIndex;
            return this;
        }

        /**
         * 设置最大重试次数.
         *
         * @param maxRetries 最大重试次数（&gt;=0）
         * @return 当前 Builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * 设置重试基础退避时间（毫秒）.
         *
         * @param backoffMs 基础退避毫秒数
         * @return 当前 Builder
         */
        public Builder backoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
            return this;
        }

        /**
         * 设置退避倍率.
         *
         * @param backoffMultiplier 退避倍率（如 2.0 表示指数退避）
         * @return 当前 Builder
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * 设置任务处理器（必填）.
         *
         * @param handler 任务处理器
         * @return 当前 Builder
         */
        public Builder handler(JobHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 添加一个自定义参数.
         *
         * @param key   参数键
         * @param value 参数值
         * @return 当前 Builder
         */
        public Builder param(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        /**
         * 批量添加自定义参数.
         *
         * @param params 参数Map
         * @return 当前 Builder
         */
        public Builder params(Map<String, Object> params) {
            this.params.putAll(params);
            return this;
        }

        /**
         * 构建 {@link JobDefinition} 实例.
         *
         * <p>构建前会校验必填字段 jobName 和 handler 是否已设置.</p>
         *
         * @return 新的 JobDefinition 实例
         * @throws IllegalArgumentException 如果 jobName 或 handler 未设置
         */
        public JobDefinition build() {
            if (jobName == null || jobName.trim().isEmpty()) {
                throw new IllegalArgumentException("jobName 不能为空");
            }
            if (handler == null) {
                throw new IllegalArgumentException("handler 不能为 null");
            }
            return new JobDefinition(this);
        }
    }

    // ======================== 标准方法 ========================

    @Override
    public String toString() {
        return "JobDefinition{" +
                "jobName='" + jobName + '\'' +
                ", cron='" + cron + '\'' +
                ", description='" + description + '\'' +
                ", shardTotal=" + shardTotal +
                ", shardIndex=" + shardIndex +
                ", maxRetries=" + maxRetries +
                '}';
    }
}
