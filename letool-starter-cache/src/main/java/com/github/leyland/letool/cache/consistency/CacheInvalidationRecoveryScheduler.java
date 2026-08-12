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
        Duration effectiveInterval = interval == null || interval.isZero() || interval.isNegative()
                ? Duration.ofSeconds(5) : interval;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "letool-cache-outbox-recovery");
            thread.setDaemon(true);
            return thread;
        });
        long delayMillis = effectiveInterval.toMillis();
        executor.scheduleWithFixedDelay(
                () -> recover(recovery), delayMillis, delayMillis, TimeUnit.MILLISECONDS);
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
