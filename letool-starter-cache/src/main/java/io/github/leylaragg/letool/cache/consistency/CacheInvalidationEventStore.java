package io.github.leylaragg.letool.cache.consistency;

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
    default boolean markCompleted(String eventId, String leaseOwner) {
        markCompleted(eventId);
        return true;
    }

    /**
     * 兼容旧版事件仓储实现；新实现应覆盖带租约令牌的方法。
     *
     * @param eventId 事件 ID
     */
    @Deprecated
    default void markCompleted(String eventId) {
        markCompleted(eventId, null);
    }

    /**
     * 记录一次失败，并安排下一次重试时间。
     *
     * @param eventId 事件 ID
     * @param nextAttemptAt 下一次允许处理的时间
     */
    default boolean markRetry(String eventId, String leaseOwner, Instant nextAttemptAt) {
        markRetry(eventId, nextAttemptAt);
        return true;
    }

    /**
     * 兼容旧版事件仓储实现；新实现应覆盖带租约令牌的方法。
     */
    @Deprecated
    default void markRetry(String eventId, Instant nextAttemptAt) {
        markRetry(eventId, null, nextAttemptAt);
    }

    /**
     * 查询 Outbox 当前积压和已完成事件数量。
     *
     * @param now 当前时间
     * @return 积压快照
     */
    default CacheInvalidationBacklog backlog(Instant now) {
        return new CacheInvalidationBacklog(0, 0, 0, null);
    }

    /**
     * 分批删除超过保留期的已完成事件。
     *
     * @param completedBefore 删除此时间之前完成的事件
     * @param batchSize 单批最大删除数量
     * @return 实际删除数量
     */
    default int deleteCompletedBefore(Instant completedBefore, int batchSize) {
        return 0;
    }
}
