package io.github.leylaragg.letool.monitor.cleanup;

import java.time.Instant;

/**
 * 单个数据清理任务的不可变执行上下文。
 *
 * @param taskName 任务名称
 * @param triggeredAt 本轮统一触发时间
 * @param cutoff 应清理数据的截止时间
 */
public record CleanupContext(
        String taskName,
        Instant triggeredAt,
        Instant cutoff) {

    /**
     * 校验清理上下文。
     *
     * @param taskName 任务名称
     * @param triggeredAt 本轮统一触发时间
     * @param cutoff 应清理数据的截止时间
     */
    public CleanupContext {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName 不能为空");
        }
        if (triggeredAt == null) {
            throw new IllegalArgumentException("triggeredAt 不能为空");
        }
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff 不能为空");
        }
        if (cutoff.isAfter(triggeredAt)) {
            throw new IllegalArgumentException("cutoff 不能晚于 triggeredAt");
        }
        taskName = taskName.trim();
    }
}
