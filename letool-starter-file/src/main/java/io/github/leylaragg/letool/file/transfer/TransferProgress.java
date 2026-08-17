package io.github.leylaragg.letool.file.transfer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次文件传输的不可变进度快照。
 *
 * @param transferId 传输编号
 * @param type 传输类型
 * @param status 当前状态
 * @param totalBytes 总字节数；未知时为 {@code -1}
 * @param transferredBytes 已确认传输字节数
 * @param percentage 完成百分比；总量未知时为 {@code -1}
 * @param bytesPerSecond 平均每秒传输字节数
 * @param estimatedRemaining 估算剩余时间；无法估算时为空
 * @param createdAt 创建时间
 * @param startedAt 开始时间
 * @param updatedAt 最近更新时间
 * @param completedAt 进入终态的时间；未完成时为空
 * @param failureReason 对业务安全的失败原因；非失败状态时为空
 */
public record TransferProgress(
        String transferId,
        TransferType type,
        TransferStatus status,
        long totalBytes,
        long transferredBytes,
        double percentage,
        long bytesPerSecond,
        Duration estimatedRemaining,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String failureReason) {

    /**
     * 校验进度快照的基础不变量。
     *
     * @param transferId 传输编号
     * @param type 传输类型
     * @param status 当前状态
     * @param totalBytes 总字节数
     * @param transferredBytes 已确认传输字节数
     * @param percentage 完成百分比
     * @param bytesPerSecond 平均速度
     * @param estimatedRemaining 估算剩余时间
     * @param createdAt 创建时间
     * @param startedAt 开始时间
     * @param updatedAt 最近更新时间
     * @param completedAt 终态时间
     * @param failureReason 安全失败原因
     */
    public TransferProgress {
        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException("transferId 不能为空");
        }
        type = Objects.requireNonNull(type, "type 不能为空");
        status = Objects.requireNonNull(status, "status 不能为空");
        if (totalBytes < -1 || transferredBytes < 0
                || (totalBytes >= 0 && transferredBytes > totalBytes)) {
            throw new IllegalArgumentException("传输字节数不合法");
        }
        if (bytesPerSecond < 0) {
            throw new IllegalArgumentException("bytesPerSecond 不能小于 0");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }
}
