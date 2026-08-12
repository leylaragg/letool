package com.github.leyland.letool.cache.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * DURABLE Outbox 事件恢复处理器。
 */
public class CacheInvalidationRecovery {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationRecovery.class);

    private final CacheInvalidationEventStore eventStore;
    private final RedisCacheFenceStore fenceStore;
    private final String owner;
    private final int batchSize;
    private final Duration lease;
    private final Duration retryBaseDelay;

    /**
     * 创建一次批量恢复处理器。
     */
    public CacheInvalidationRecovery(
            CacheInvalidationEventStore eventStore,
            RedisCacheFenceStore fenceStore,
            String owner,
            int batchSize,
            Duration lease,
            Duration retryBaseDelay) {
        this.eventStore = Objects.requireNonNull(eventStore, "事件仓储不能为空");
        this.fenceStore = Objects.requireNonNull(fenceStore, "Redis 围栏存储不能为空");
        this.owner = Objects.requireNonNull(owner, "恢复实例标识不能为空");
        this.batchSize = batchSize;
        this.lease = Objects.requireNonNull(lease, "恢复租约不能为空");
        this.retryBaseDelay = Objects.requireNonNull(retryBaseDelay, "重试基础延迟不能为空");
        if (batchSize <= 0 || lease.isZero() || lease.isNegative()
                || retryBaseDelay.isZero() || retryBaseDelay.isNegative()) {
            throw new IllegalArgumentException("批量数量、租约和重试延迟必须大于零");
        }
    }

    /**
     * 领取并处理一批到期事件。
     *
     * @param now 当前时间
     * @return 成功处理数量
     */
    public int recoverOnce(Instant now) {
        List<CacheInvalidationEvent> events = eventStore.claimBatch(
                now, batchSize, lease, owner);
        int completed = 0;
        for (CacheInvalidationEvent event : events) {
            try {
                CacheFence fence = new CacheFence(
                        event.cacheName(), event.serializedKey(), event.eventId(),
                        event.fenceToken(), event.createdAt());
                CacheFenceCompletion completion = fenceStore.complete(fence);
                // SUPERSEDED 表示该事件建立围栏时的旧数据早已删除，且当前围栏属于更新事务。
                if (completion != null) {
                    eventStore.markCompleted(event.eventId());
                    completed++;
                }
            } catch (Exception exception) {
                Duration delay = retryDelay(event.attemptCount());
                eventStore.markRetry(event.eventId(), now.plus(delay));
                log.warn("Durable cache invalidation recovery failed, eventId={}, attempt={}",
                        event.eventId(), event.attemptCount() + 1);
                log.debug("Durable cache invalidation recovery detail", exception);
            }
        }
        return completed;
    }

    private Duration retryDelay(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount, 0), 10);
        return retryBaseDelay.multipliedBy(1L << exponent);
    }
}
