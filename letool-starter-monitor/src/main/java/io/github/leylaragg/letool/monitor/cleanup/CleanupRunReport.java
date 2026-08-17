package io.github.leylaragg.letool.monitor.cleanup;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 一轮数据清理的不可变执行报告。
 *
 * @param startedAt 本轮开始时间
 * @param finishedAt 本轮完成时间
 * @param overlapSkipped 是否因已有任务运行而跳过
 * @param executions 单任务执行结果
 */
public record CleanupRunReport(
        Instant startedAt,
        Instant finishedAt,
        boolean overlapSkipped,
        List<CleanupExecution> executions) {

    /**
     * 校验报告并复制执行结果列表。
     *
     * @param startedAt 本轮开始时间
     * @param finishedAt 本轮完成时间
     * @param overlapSkipped 是否因已有任务运行而跳过
     * @param executions 单任务执行结果
     */
    public CleanupRunReport {
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("报告时间不能为空");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt 不能早于 startedAt");
        }
        if (executions == null) {
            throw new IllegalArgumentException("executions 不能为空");
        }
        executions = List.copyOf(executions);
        if (overlapSkipped && !executions.isEmpty()) {
            throw new IllegalArgumentException("重入跳过报告不能包含执行结果");
        }
    }

    /**
     * 获取成功任务数量。
     *
     * @return 成功任务数量
     */
    public long successCount() {
        return executions.stream()
                .filter(execution ->
                        execution.status() == CleanupExecutionStatus.SUCCESS)
                .count();
    }

    /**
     * 获取失败任务数量。
     *
     * @return 失败任务数量
     */
    public long failureCount() {
        return executions.stream()
                .filter(execution ->
                        execution.status() == CleanupExecutionStatus.FAILED)
                .count();
    }

    /**
     * 获取本轮受影响记录总数。
     *
     * @return 所有成功任务的受影响记录数之和
     */
    public long totalAffectedRows() {
        return executions.stream()
                .mapToLong(CleanupExecution::affectedRows)
                .sum();
    }

    /**
     * 获取本轮执行耗时。
     *
     * @return 开始到完成的时长
     */
    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    /**
     * 创建因重入保护而跳过的报告。
     *
     * @param occurredAt 跳过发生时间
     * @return 空执行列表的跳过报告
     */
    static CleanupRunReport overlapSkipped(Instant occurredAt) {
        return new CleanupRunReport(
                occurredAt,
                occurredAt,
                true,
                List.of());
    }
}
