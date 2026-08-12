package com.github.leyland.letool.cache.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时重放 DURABLE Outbox 事件的后台调度器。
 */
public class CacheInvalidationRecoveryScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationRecoveryScheduler.class);
    private final ScheduledExecutorService executor;

    /**
     * 创建并启动恢复调度器。
     *
     * @param recovery 单批恢复处理器
     * @param interval 扫描间隔
     */
    public CacheInvalidationRecoveryScheduler(CacheInvalidationRecovery recovery, Duration interval) {
        this(recovery, interval, Duration.ofHours(1), Duration.ofDays(7), 1000);
    }

    /**
     * 创建并启动恢复与已完成事件清理调度器。
     *
     * @param recovery 恢复处理器
     * @param interval 恢复扫描间隔
     * @param cleanupInterval 已完成事件清理间隔
     * @param completedRetention 已完成事件保留时间
     * @param cleanupBatchSize 单次清理数量
     */
    public CacheInvalidationRecoveryScheduler(
            CacheInvalidationRecovery recovery,
            Duration interval,
            Duration cleanupInterval,
            Duration completedRetention,
            int cleanupBatchSize) {
        Duration effectiveInterval = interval == null || interval.isZero() || interval.isNegative()
                ? Duration.ofSeconds(5) : interval;
        Duration effectiveCleanupInterval = cleanupInterval == null
                || cleanupInterval.isZero() || cleanupInterval.isNegative()
                ? Duration.ofHours(1) : cleanupInterval;
        Duration effectiveRetention = completedRetention == null
                || completedRetention.isZero() || completedRetention.isNegative()
                ? Duration.ofDays(7) : completedRetention;
        int effectiveCleanupBatchSize = cleanupBatchSize <= 0 ? 1000 : cleanupBatchSize;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "letool-cache-outbox-recovery");
            thread.setDaemon(true);
            return thread;
        });
        long delayMillis = effectiveInterval.toMillis();
        executor.scheduleWithFixedDelay(
                () -> recover(recovery), delayMillis, delayMillis, TimeUnit.MILLISECONDS);
        long cleanupDelayMillis = effectiveCleanupInterval.toMillis();
        executor.scheduleWithFixedDelay(
                () -> cleanup(recovery, effectiveRetention, effectiveCleanupBatchSize),
                cleanupDelayMillis, cleanupDelayMillis, TimeUnit.MILLISECONDS);
    }

    private void cleanup(
            CacheInvalidationRecovery recovery, Duration completedRetention, int cleanupBatchSize) {
        try {
            int deleted = recovery.cleanupCompleted(
                    Instant.now(), completedRetention, cleanupBatchSize);
            if (deleted > 0) {
                log.info("Durable cache completed outbox events cleaned, count={}", deleted);
            }
        } catch (Exception exception) {
            log.warn("Durable cache completed outbox cleanup failed", exception);
        }
    }

    private void recover(CacheInvalidationRecovery recovery) {
        try {
            recovery.recoverOnce(Instant.now());
        } catch (Exception exception) {
            log.warn("Durable cache invalidation recovery scan failed", exception);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
