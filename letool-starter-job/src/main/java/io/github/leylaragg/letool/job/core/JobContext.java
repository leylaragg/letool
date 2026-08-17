package io.github.leylaragg.letool.job.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 传递给业务处理器的不可变任务执行上下文。
 *
 * <p>上下文只暴露稳定业务元数据，不暴露 Quartz 可变对象。</p>
 */
public final class JobContext {

    private final String executionId;
    private final String jobName;
    private final int shardIndex;
    private final int shardTotal;
    private final int retryCount;
    private final JobTriggerType triggerType;
    private final Instant scheduledFireTime;
    private final Instant startTime;
    private final String fireInstanceId;
    private final String schedulerInstanceId;
    private final Map<String, String> params;

    /**
     * 创建完整执行上下文。
     *
     * @param executionId 同一首次执行及其重试共享的执行标识
     * @param jobName 逻辑任务名称
     * @param shardIndex 分片索引
     * @param shardTotal 分片总数
     * @param retryCount 当前重试次数，首次执行为零
     * @param triggerType 触发来源
     * @param scheduledFireTime 计划触发时间
     * @param startTime 实际开始时间
     * @param fireInstanceId Quartz 单次触发标识
     * @param schedulerInstanceId 当前调度节点标识
     * @param params 不可持久化对象之外的字符串参数
     */
    public JobContext(
            String executionId,
            String jobName,
            int shardIndex,
            int shardTotal,
            int retryCount,
            JobTriggerType triggerType,
            Instant scheduledFireTime,
            Instant startTime,
            String fireInstanceId,
            String schedulerInstanceId,
            Map<String, String> params) {
        this.executionId = requireText(executionId, "executionId 不能为空");
        this.jobName = requireText(jobName, "jobName 不能为空");
        if (shardTotal <= 0 || shardIndex < 0 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException("分片索引必须位于有效范围内");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount 不能小于 0");
        }
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.retryCount = retryCount;
        this.triggerType = requireNonNull(triggerType, "triggerType 不能为 null");
        this.scheduledFireTime = requireNonNull(scheduledFireTime, "scheduledFireTime 不能为 null");
        this.startTime = requireNonNull(startTime, "startTime 不能为 null");
        this.fireInstanceId = requireText(fireInstanceId, "fireInstanceId 不能为空");
        this.schedulerInstanceId = requireText(schedulerInstanceId, "schedulerInstanceId 不能为空");
        this.params = immutableParameters(params);
    }

    /** @return 执行标识 */
    public String getExecutionId() { return executionId; }
    /** @return 任务名称 */
    public String getJobName() { return jobName; }
    /** @return 分片索引 */
    public int getShardIndex() { return shardIndex; }
    /** @return 分片总数 */
    public int getShardTotal() { return shardTotal; }
    /** @return 当前重试次数 */
    public int getRetryCount() { return retryCount; }
    /** @return 触发来源 */
    public JobTriggerType getTriggerType() { return triggerType; }
    /** @return 计划触发时间 */
    public Instant getScheduledFireTime() { return scheduledFireTime; }
    /** @return 实际开始时间 */
    public Instant getStartTime() { return startTime; }
    /** @return Quartz 单次触发标识 */
    public String getFireInstanceId() { return fireInstanceId; }
    /** @return 调度节点标识 */
    public String getSchedulerInstanceId() { return schedulerInstanceId; }
    /** @return 不可变字符串参数 */
    public Map<String, String> getParams() { return params; }
    /**
     * 读取字符串参数。
     *
     * @param key 参数键
     * @return 参数值；不存在时返回 {@code null}
     */
    public String getParam(String key) {
        return params.get(key);
    }

    /**
     * 按目标类型安全读取字符串参数。
     *
     * @param key 参数键
     * @param type 目标类型；当前仅支持字符串及其父类型
     * @param <T> 目标类型
     * @return 存在且类型兼容时返回参数
     */
    public <T> Optional<T> getParam(String key, Class<T> type) {
        String value = params.get(key);
        return value != null && type != null && type.isInstance(value)
                ? Optional.of(type.cast(value)) : Optional.empty();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 防御性复制并校验业务参数。
     *
     * @param source 原始参数；为 {@code null} 时按空参数处理
     * @return 不可变字符串参数
     */
    private static Map<String, String> immutableParameters(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (source == null) {
            return Collections.unmodifiableMap(copy);
        }
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("任务上下文参数键不能为空");
            }
            if (value == null) {
                throw new IllegalArgumentException("任务上下文参数值不能为 null");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
