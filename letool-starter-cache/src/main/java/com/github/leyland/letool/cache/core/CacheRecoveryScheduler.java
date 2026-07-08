package com.github.leyland.letool.cache.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * L2 降级恢复调度器。
 *
 * <p>当某个缓存实例访问 Redis 失败时，会进入 L2 降级状态，并把自己的缓存名称登记到
 * {@link CacheManager} 的降级集合中。本调度器定时调用 {@link CacheManager#tryRecoverAll()}，
 * 只探测这些已经降级的缓存。</p>
 *
 * <p>恢复探测是轻量操作：只检查 Redis 是否恢复可访问，不会预热数据，也不会遍历所有业务 key。
 * 这样可以避免 Redis 刚恢复时被缓存框架主动打满。</p>
 */
public class CacheRecoveryScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CacheRecoveryScheduler.class);

    /** 单线程后台调度器，daemon 线程不会阻止应用退出。 */
    private final ScheduledExecutorService executor;

    public CacheRecoveryScheduler(CacheManager cacheManager, Duration interval) {
        Duration effectiveInterval = interval == null || interval.isZero() || interval.isNegative()
                ? Duration.ofSeconds(30)
                : interval;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "letool-cache-recovery");
            thread.setDaemon(true);
            return thread;
        });
        long delayMillis = effectiveInterval.toMillis();
        executor.scheduleWithFixedDelay(() -> recover(cacheManager), delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void recover(CacheManager cacheManager) {
        try {
            // CacheManager 内部只扫描降级缓存集合，不扫全量缓存实例。
            cacheManager.tryRecoverAll();
        } catch (Exception e) {
            log.warn("Cache recovery scan failed", e);
        }
    }

    @Override
    public void close() {
        // Spring 销毁 Bean 时关闭调度线程，避免测试或应用关闭后线程残留。
        executor.shutdownNow();
    }
}
