package com.github.leyland.letool.cache.consistency;

import java.time.Instant;

/**
 * DURABLE Outbox 的轻量积压快照。
 *
 * @param pendingCount 等待处理数量
 * @param processingCount 已领取但尚未完成数量
 * @param completedCount 已完成且尚未清理数量
 * @param oldestOutstandingCreatedAt 最早未完成事件创建时间；没有积压时为 {@code null}
 */
public record CacheInvalidationBacklog(
        long pendingCount,
        long processingCount,
        long completedCount,
        Instant oldestOutstandingCreatedAt) {

    /** @return 当前未完成事件总数。 */
    public long outstandingCount() {
        return pendingCount + processingCount;
    }
}
