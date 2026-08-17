package io.github.leylaragg.letool.job.core;

import java.time.Duration;
import java.time.Instant;

/**
 * 描述一次实际任务执行尝试的不可变记录。
 */
public final class JobExecutionRecord {

    private final JobContext context;
    private final JobStatus status;
    private final Instant endTime;
    private final long durationMs;
    private final String errorMessage;

    private JobExecutionRecord(
            JobContext context,
            JobStatus status,
            Instant endTime,
            String errorMessage) {
        if (context == null || status == null || endTime == null) {
            throw new IllegalArgumentException("执行记录必填字段不能为 null");
        }
        if (endTime.isBefore(context.getStartTime())) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        this.context = context;
        this.status = status;
        this.endTime = endTime;
        this.durationMs = Duration.between(context.getStartTime(), endTime).toMillis();
        this.errorMessage = errorMessage;
    }

    /**
     * 创建执行成功记录。
     *
     * @param context 执行上下文
     * @param endTime 结束时间
     * @return 成功记录
     */
    public static JobExecutionRecord success(JobContext context, Instant endTime) {
        return new JobExecutionRecord(context, JobStatus.SUCCESS, endTime, null);
    }

    /**
     * 创建已安排重试记录。
     *
     * @param context 执行上下文
     * @param endTime 结束时间
     * @param errorMessage 安全错误摘要
     * @return 已安排重试记录
     */
    public static JobExecutionRecord retryScheduled(JobContext context, Instant endTime, String errorMessage) {
        return new JobExecutionRecord(context, JobStatus.RETRY_SCHEDULED, endTime, errorMessage);
    }

    /**
     * 创建最终失败记录。
     *
     * @param context 执行上下文
     * @param endTime 结束时间
     * @param errorMessage 安全错误摘要
     * @return 最终失败记录
     */
    public static JobExecutionRecord failed(JobContext context, Instant endTime, String errorMessage) {
        return new JobExecutionRecord(context, JobStatus.FAILED, endTime, errorMessage);
    }

    /** @return 执行标识 */
    public String getExecutionId() { return context.getExecutionId(); }
    /** @return 任务名称 */
    public String getJobName() { return context.getJobName(); }
    /** @return 分片索引 */
    public int getShardIndex() { return context.getShardIndex(); }
    /** @return 分片总数 */
    public int getShardTotal() { return context.getShardTotal(); }
    /** @return 当前重试次数 */
    public int getRetryCount() { return context.getRetryCount(); }
    /** @return 触发来源 */
    public JobTriggerType getTriggerType() { return context.getTriggerType(); }
    /** @return 执行状态 */
    public JobStatus getStatus() { return status; }
    /** @return Quartz 计划触发时间 */
    public Instant getScheduledFireTime() { return context.getScheduledFireTime(); }
    /** @return 实际开始时间 */
    public Instant getStartTime() { return context.getStartTime(); }
    /** @return 结束时间 */
    public Instant getEndTime() { return endTime; }
    /** @return 执行耗时毫秒数 */
    public long getDurationMs() { return durationMs; }
    /** @return 安全错误摘要；成功时返回 {@code null} */
    public String getErrorMessage() { return errorMessage; }
    /** @return Quartz 单次触发标识 */
    public String getFireInstanceId() { return context.getFireInstanceId(); }
    /** @return 调度节点标识 */
    public String getSchedulerInstanceId() { return context.getSchedulerInstanceId(); }
}
