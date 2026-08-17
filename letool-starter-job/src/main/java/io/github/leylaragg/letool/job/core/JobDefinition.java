package io.github.leylaragg.letool.job.core;

import org.quartz.CronExpression;

import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述一个可持久化调度任务的不可变元数据。
 *
 * <p>任务定义只保存能够稳定写入 Quartz JobDataMap 的配置，不保存 Spring Bean、
 * Lambda 或任意业务对象。任务处理器由调度门面单独关联。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class JobDefinition {

    /** Letool 内部 JobDataMap 键的保留前缀。 */
    public static final String RESERVED_PARAMETER_PREFIX = "letool.internal.";

    private final String jobName;
    private final String cron;
    private final String zone;
    private final String description;
    private final int shardTotal;
    private final int maxRetries;
    private final long backoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final boolean concurrent;
    private final MisfirePolicy misfirePolicy;
    private final boolean requestRecovery;
    private final Map<String, String> params;

    private JobDefinition(Builder builder) {
        this.jobName = requireText(builder.jobName, "jobName 不能为空");
        this.cron = normalize(builder.cron);
        validateCron(this.cron);
        this.zone = normalize(builder.zone);
        validateZone(this.zone);
        this.description = builder.description == null ? "" : builder.description;
        this.shardTotal = requirePositive(builder.shardTotal, "shardTotal 必须大于 0");
        this.maxRetries = requireNonNegative(builder.maxRetries, "maxRetries 不能小于 0");
        this.backoffMs = requireNonNegative(builder.backoffMs, "backoffMs 不能小于 0");
        if (!Double.isFinite(builder.backoffMultiplier) || builder.backoffMultiplier <= 0) {
            throw new IllegalArgumentException("backoffMultiplier 必须为有限正数");
        }
        this.backoffMultiplier = builder.backoffMultiplier;
        this.maxBackoffMs = requirePositive(builder.maxBackoffMs, "maxBackoffMs 必须大于 0");
        this.concurrent = builder.concurrent;
        this.misfirePolicy = builder.misfirePolicy == null
                ? MisfirePolicy.DO_NOTHING : builder.misfirePolicy;
        this.requestRecovery = builder.requestRecovery;
        this.params = immutableParameters(builder.params);
    }

    /**
     * 创建任务定义建造器。
     *
     * @return 新建造器
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 逻辑任务名称 */
    public String getJobName() {
        return jobName;
    }

    /** @return Quartz Cron；手动任务返回 {@code null} */
    public String getCron() {
        return cron;
    }

    /** @return 显式时区 ID；使用默认时区时返回 {@code null} */
    public String getZone() {
        return zone;
    }

    /** @return 任务说明 */
    public String getDescription() {
        return description;
    }

    /** @return 分片总数 */
    public int getShardTotal() {
        return shardTotal;
    }

    /** @return 最大额外重试次数 */
    public int getMaxRetries() {
        return maxRetries;
    }

    /** @return 第一次重试延迟毫秒数 */
    public long getBackoffMs() {
        return backoffMs;
    }

    /** @return 重试退避倍率 */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /** @return 单次重试最大延迟毫秒数 */
    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    /** @return 是否允许同一分片并发 */
    public boolean isConcurrent() {
        return concurrent;
    }

    /** @return Cron 错过触发策略 */
    public MisfirePolicy getMisfirePolicy() {
        return misfirePolicy;
    }

    /** @return 是否请求 Quartz 节点故障恢复 */
    public boolean isRequestRecovery() {
        return requestRecovery;
    }

    /** @return 不可变字符串参数 */
    public Map<String, String> getParams() {
        return params;
    }

    private static Map<String, String> immutableParameters(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = requireText(key, "任务参数键不能为空");
            if (safeKey.startsWith(RESERVED_PARAMETER_PREFIX)) {
                throw new IllegalArgumentException("任务参数不能使用 Letool 内部保留前缀");
            }
            if (value == null) {
                throw new IllegalArgumentException("任务参数值不能为 null");
            }
            copy.put(safeKey, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void validateCron(String cron) {
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("cron 不是有效的 Quartz Cron 表达式");
        }
    }

    private static void validateZone(String zone) {
        if (zone != null) {
            try {
                ZoneId.of(zone);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("zone 不是有效的时区 ID", exception);
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requireNonNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * {@link JobDefinition} 建造器。
     */
    public static final class Builder {
        private String jobName;
        private String cron;
        private String zone;
        private String description = "";
        private int shardTotal = 1;
        private int maxRetries;
        private long backoffMs = 1_000;
        private double backoffMultiplier = 2.0;
        private long maxBackoffMs = 60_000;
        private boolean concurrent;
        private MisfirePolicy misfirePolicy = MisfirePolicy.DO_NOTHING;
        private boolean requestRecovery;
        private final Map<String, String> params = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 设置逻辑任务名称。
         *
         * @param jobName 逻辑任务名称
         * @return 当前建造器
         */
        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        /**
         * 设置 Quartz Cron 表达式。
         *
         * @param cron Quartz Cron；为空时表示仅允许手动触发
         * @return 当前建造器
         */
        public Builder cron(String cron) {
            this.cron = cron;
            return this;
        }

        /**
         * 设置 Cron 时区。
         *
         * @param zone 标准时区 ID；为空时使用 Quartz 默认时区
         * @return 当前建造器
         */
        public Builder zone(String zone) {
            this.zone = zone;
            return this;
        }

        /**
         * 设置任务说明。
         *
         * @param description 任务说明
         * @return 当前建造器
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置分片总数。
         *
         * @param shardTotal 分片总数
         * @return 当前建造器
         */
        public Builder shardTotal(int shardTotal) {
            this.shardTotal = shardTotal;
            return this;
        }

        /**
         * 设置最大额外重试次数。
         *
         * @param maxRetries 最大额外重试次数
         * @return 当前建造器
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * 设置第一次重试延迟。
         *
         * @param backoffMs 第一次重试延迟毫秒数
         * @return 当前建造器
         */
        public Builder backoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
            return this;
        }

        /**
         * 设置重试退避倍率。
         *
         * @param multiplier 有限正数倍率
         * @return 当前建造器
         */
        public Builder backoffMultiplier(double multiplier) {
            this.backoffMultiplier = multiplier;
            return this;
        }

        /**
         * 设置单次重试最大延迟。
         *
         * @param maxBackoffMs 单次重试最大延迟毫秒数
         * @return 当前建造器
         */
        public Builder maxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }

        /**
         * 设置同一分片是否允许并发执行。
         *
         * @param concurrent 是否允许并发执行
         * @return 当前建造器
         */
        public Builder concurrent(boolean concurrent) {
            this.concurrent = concurrent;
            return this;
        }

        /**
         * 设置 Cron 错过触发策略。
         *
         * @param policy Cron 错过触发策略
         * @return 当前建造器
         */
        public Builder misfirePolicy(MisfirePolicy policy) {
            this.misfirePolicy = policy;
            return this;
        }

        /**
         * 设置是否请求 Quartz 节点故障恢复。
         *
         * @param requestRecovery 是否请求恢复
         * @return 当前建造器
         */
        public Builder requestRecovery(boolean requestRecovery) {
            this.requestRecovery = requestRecovery;
            return this;
        }

        /**
         * 添加一个可持久化字符串参数。
         *
         * @param key 参数键
         * @param value 参数值
         * @return 当前建造器
         */
        public Builder param(String key, String value) {
            this.params.put(key, value);
            return this;
        }

        /**
         * 批量添加可持久化字符串参数。
         *
         * @param params 字符串参数
         * @return 当前建造器
         */
        public Builder params(Map<String, String> params) {
            if (params == null) {
                throw new IllegalArgumentException("params 不能为 null");
            }
            this.params.putAll(params);
            return this;
        }

        /**
         * 校验并创建不可变任务定义。
         *
         * @return 校验完成的不可变任务定义
         */
        public JobDefinition build() {
            return new JobDefinition(this);
        }
    }
}
