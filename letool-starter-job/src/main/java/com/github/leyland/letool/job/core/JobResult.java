package com.github.leyland.letool.job.core;

import java.time.LocalDateTime;

/**
 * 任务执行结果——记录单次任务执行的完整信息.
 *
 * <p>每次任务执行完成后，调度器会创建一个 {@code JobResult} 实例，
 * 包含执行状态、耗时、结果摘要、异常信息等，供日志服务和监控查询使用.</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@link #executionId} — 执行唯一标识</li>
 *   <li>{@link #jobName} — 任务名称</li>
 *   <li>{@link #status} — 执行状态（RUNNING/SUCCESS/FAIL/TIMEOUT）</li>
 *   <li>{@link #startTime} — 开始时间</li>
 *   <li>{@link #endTime} — 结束时间</li>
 *   <li>{@link #durationMs} — 执行耗时（毫秒）</li>
 *   <li>{@link #result} — 结果摘要文本</li>
 *   <li>{@link #errorMessage} — 错误信息（仅失败时有值）</li>
 *   <li>{@link #retryCount} — 重试次数</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobStatus
 * @see JobLogService
 */
public class JobResult {

    // ======================== 成员变量 ========================

    /**
     * 执行唯一标识.
     */
    private final String executionId;

    /**
     * 任务名称.
     */
    private final String jobName;

    /**
     * 执行状态.
     */
    private JobStatus status;

    /**
     * 执行开始时间.
     */
    private final LocalDateTime startTime;

    /**
     * 执行结束时间.
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）.
     */
    private long durationMs;

    /**
     * 结果摘要文本.
     */
    private String result;

    /**
     * 错误信息（仅失败时有值）.
     */
    private String errorMessage;

    /**
     * 重试次数.
     */
    private int retryCount;

    // ======================== 构造方法 ========================

    /**
     * 创建任务执行结果（初始状态为 RUNNING）.
     *
     * @param executionId 执行唯一标识
     * @param jobName     任务名称
     */
    public JobResult(String executionId, String jobName) {
        this.executionId = executionId;
        this.jobName = jobName;
        this.status = JobStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 从 JobContext 创建任务执行结果（初始状态为 RUNNING）.
     *
     * @param context 任务执行上下文
     */
    public JobResult(JobContext context) {
        this(context.getExecutionId(), context.getJobName());
    }

    // ======================== 构建方法 ========================

    /**
     * 将执行结果标记为成功.
     *
     * <p>记录结束时间、计算耗时并设置状态为 SUCCESS.</p>
     *
     * @param result 结果摘要文本
     * @return 当前实例（链式调用）
     */
    public JobResult success(String result) {
        this.status = JobStatus.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.result = result;
        return this;
    }

    /**
     * 将执行结果标记为失败.
     *
     * <p>记录结束时间、计算耗时，设置状态为 FAIL，并保存错误信息.</p>
     *
     * @param errorMessage 错误信息
     * @param retryCount   已重试次数
     * @return 当前实例（链式调用）
     */
    public JobResult fail(String errorMessage, int retryCount) {
        this.status = JobStatus.FAIL;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        return this;
    }

    /**
     * 将执行结果标记为超时.
     *
     * <p>设置状态为 TIMEOUT，记录结束时间和错误信息.</p>
     *
     * @param errorMessage 超时描述信息
     * @return 当前实例（链式调用）
     */
    public JobResult timeout(String errorMessage) {
        this.status = JobStatus.TIMEOUT;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.errorMessage = errorMessage;
        return this;
    }

    // ======================== Getter 方法 ========================

    /**
     * 获取执行唯一标识.
     *
     * @return 执行ID
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * 获取任务名称.
     *
     * @return 任务名称
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 获取执行状态.
     *
     * @return 执行状态
     */
    public JobStatus getStatus() {
        return status;
    }

    /**
     * 获取执行开始时间.
     *
     * @return 开始时间
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * 获取执行结束时间.
     *
     * @return 结束时间（未完成时可能为 {@code null}）
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * 获取执行耗时（毫秒）.
     *
     * @return 耗时毫秒数（未完成时为0）
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * 获取结果摘要文本.
     *
     * @return 结果摘要
     */
    public String getResult() {
        return result;
    }

    /**
     * 获取错误信息.
     *
     * @return 错误信息（成功时为 {@code null}）
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取重试次数.
     *
     * @return 重试次数
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 判断本次执行是否成功.
     *
     * @return {@code true} 如果状态为 SUCCESS
     */
    public boolean isSuccess() {
        return status == JobStatus.SUCCESS;
    }

    /**
     * 判断本次执行是否失败.
     *
     * @return {@code true} 如果状态为 FAIL
     */
    public boolean isFailed() {
        return status == JobStatus.FAIL;
    }

    // ======================== 标准方法 ========================

    @Override
    public String toString() {
        return "JobResult{" +
                "executionId='" + executionId + '\'' +
                ", jobName='" + jobName + '\'' +
                ", status=" + status +
                ", durationMs=" + durationMs +
                ", retryCount=" + retryCount +
                '}';
    }
}
