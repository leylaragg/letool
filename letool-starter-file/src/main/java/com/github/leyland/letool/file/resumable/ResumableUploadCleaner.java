package com.github.leyland.letool.file.resumable;

import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时清理过期断点续传会话的可关闭生命周期组件。
 */
public final class ResumableUploadCleaner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ResumableUploadCleaner.class);

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建并启动单线程守护清理器。
     *
     * @param service 断点续传服务
     * @param cleanupInterval 清理间隔
     */
    public ResumableUploadCleaner(
            ResumableUploadService service,
            Duration cleanupInterval) {
        Objects.requireNonNull(service, "service 不能为空");
        if (cleanupInterval == null
                || cleanupInterval.isZero()
                || cleanupInterval.isNegative()) {
            throw FileException.of(
                    FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.cleanup-interval");
        }
        long intervalMillis;
        try {
            intervalMillis = Math.max(1, cleanupInterval.toMillis());
        } catch (ArithmeticException exception) {
            throw FileException.causedBy(
                    FileErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "resumable.cleanup-interval");
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "letool-file-resumable-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> cleanup(service),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    /**
     * 隔离单次清理失败，避免调度器停止后不再执行。
     *
     * @param service 断点续传服务
     */
    private void cleanup(ResumableUploadService service) {
        try {
            int cleaned = service.cleanupExpired();
            if (cleaned > 0) {
                log.debug("已清理过期断点续传会话，count={}", cleaned);
            }
        } catch (RuntimeException exception) {
            log.warn("清理过期断点续传会话失败", exception);
        }
    }

    /**
     * 停止调度器并短暂等待正在执行的清理任务结束。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}
