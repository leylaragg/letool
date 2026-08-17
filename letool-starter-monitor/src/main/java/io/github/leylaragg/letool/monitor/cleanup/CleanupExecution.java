package io.github.leylaragg.letool.monitor.cleanup;

import java.time.Instant;

/**
 * 单个清理任务的不可变执行结果。
 *
 * @param taskName 任务名称
 * @param status 执行状态
 * @param startedAt 开始时间
 * @param finishedAt 完成时间
 * @param affectedRows 受影响记录数
 * @param failureType 失败异常类型；成功时为 {@code null}
 * @param failureMessage 失败摘要；成功时为 {@code null}
 */
public record CleanupExecution(
        String taskName,
        CleanupExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        long affectedRows,
        String failureType,
        String failureMessage) {

    /**
     * 校验并规范化执行结果。
     *
     * @param taskName 任务名称
     * @param status 执行状态
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     * @param affectedRows 受影响记录数
     * @param failureType 失败异常类型
     * @param failureMessage 失败摘要
     */
    public CleanupExecution {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("执行时间不能为空");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt 不能早于 startedAt");
        }
        if (affectedRows < 0) {
            throw new IllegalArgumentException("affectedRows 不能为负数");
        }
        if (status == CleanupExecutionStatus.SUCCESS
                && (failureType != null || failureMessage != null)) {
            throw new IllegalArgumentException("成功结果不能包含失败信息");
        }
        if (status == CleanupExecutionStatus.FAILED
                && (failureType == null || failureType.isBlank())) {
            throw new IllegalArgumentException("失败结果必须包含异常类型");
        }
        taskName = taskName.trim();
    }

    /**
     * 创建成功执行结果。
     *
     * @param taskName 任务名称
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     * @param affectedRows 受影响记录数
     * @return 成功执行结果
     */
    static CleanupExecution success(
            String taskName,
            Instant startedAt,
            Instant finishedAt,
            long affectedRows) {
        return new CleanupExecution(
                taskName,
                CleanupExecutionStatus.SUCCESS,
                startedAt,
                finishedAt,
                affectedRows,
                null,
                null);
    }

    /**
     * 创建失败执行结果。
     *
     * @param taskName 任务名称
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     * @param failure 失败异常
     * @return 失败执行结果
     */
    static CleanupExecution failed(
            String taskName,
            Instant startedAt,
            Instant finishedAt,
            RuntimeException failure) {
        return new CleanupExecution(
                taskName,
                CleanupExecutionStatus.FAILED,
                startedAt,
                finishedAt,
                0,
                failure.getClass().getName(),
                sanitizeFailureMessage(failure.getMessage()));
    }

    /**
     * 清理并限制失败消息长度。
     *
     * @param message 原始异常消息
     * @return 单行失败摘要
     */
    private static String sanitizeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "无错误消息";
        }
        String sanitized = message.replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        int maxLength = 500;
        return sanitized.length() <= maxLength
                ? sanitized
                : sanitized.substring(0, maxLength);
    }
}
