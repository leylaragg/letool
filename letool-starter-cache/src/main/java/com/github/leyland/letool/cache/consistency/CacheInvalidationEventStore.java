package com.github.leyland.letool.cache.consistency;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 持久化缓存失效事件的仓储扩展点。
 *
 * <p>DURABLE 模式要求实现类与业务数据使用同一个事务资源。事件写入和业务 SQL
 * 必须一起提交或回滚；恢复任务通过带租约的领取操作并发消费未完成事件。</p>
 */
public interface CacheInvalidationEventStore {

    /**
     * 在当前业务事务中写入一条待处理事件。
     *
     * @param event 缓存失效事件
     */
    void append(CacheInvalidationEvent event);

    /**
     * 条件领取一批到期事件，并为当前实例建立处理租约。
     *
     * @param now 当前时间
     * @param batchSize 最大领取数量
     * @param lease 租约时长
     * @param owner 当前实例标识
     * @return 当前实例成功领取的事件
     */
    List<CacheInvalidationEvent> claimBatch(
            Instant now, int batchSize, Duration lease, String owner);

    /**
     * 将事件标记为已完成。
     *
     * @param eventId 事件 ID
     */
    void markCompleted(String eventId);

    /**
     * 记录一次失败，并安排下一次重试时间。
     *
     * @param eventId 事件 ID
     * @param nextAttemptAt 下一次允许处理的时间
     */
    void markRetry(String eventId, Instant nextAttemptAt);
}
