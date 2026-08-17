package io.github.leylaragg.letool.job.core;

import java.time.Instant;

/**
 * Quartz 接受一次手动触发请求后返回的不可变回执。
 *
 * @param executionId 执行链标识
 * @param jobName 逻辑任务名称
 * @param shardIndex 分片索引
 * @param acceptedAt 调度器接受时间
 */
public record JobTriggerReceipt(
        String executionId,
        String jobName,
        int shardIndex,
        Instant acceptedAt) {

    /**
     * 校验触发回执字段。
     *
     * @param executionId 执行链标识
     * @param jobName 逻辑任务名称
     * @param shardIndex 分片索引
     * @param acceptedAt 调度器接受时间
     */
    public JobTriggerReceipt {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不能为空");
        }
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName 不能为空");
        }
        if (shardIndex < 0) {
            throw new IllegalArgumentException("shardIndex 不能小于 0");
        }
        if (acceptedAt == null) {
            throw new IllegalArgumentException("acceptedAt 不能为 null");
        }
    }
}
