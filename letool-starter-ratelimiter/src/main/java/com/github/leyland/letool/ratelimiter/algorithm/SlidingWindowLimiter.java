package com.github.leyland.letool.ratelimiter.algorithm;

import com.github.leyland.letool.ratelimiter.core.RateLimiter;
import com.github.leyland.letool.ratelimiter.core.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 滑动窗口（Sliding Window）限流器实现。
 *
 * <p>滑动窗口算法是固定窗口算法的改进版本，通过将时间划分为细粒度的时间片，
 * 并滑动地统计最近 N 秒内的请求数来解决固定窗口的边界突发问题。</p>
 *
 * <h3>算法原理</h3>
 * <ol>
 *   <li>维护一个双端队列（{@link Deque}），每个元素是 {@code (timestamp, count)} 对</li>
 *   <li>每次请求到达时，先将过期（超出窗口范围）的记录从队首移除</li>
 *   <li>统计当前窗口内的请求总数，若未超过 {@code maxPermits} 则允许并记录</li>
 *   <li>若超过则拒绝，并计算预估等待时间</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@code synchronized} 对每个 key 的窗口对象加锁，保证同一 key 的并发安全。
 * 不同 key 的窗口互不影响。</p>
 *
 * <h3>内存管理</h3>
 * <p>通过 {@link ScheduledExecutorService} 定期清理长时间未使用的窗口记录，
 * 防止内存泄漏。清理周期为 60 秒，超过 300 秒未访问的窗口将被移除。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimiter
 * @see TokenBucketLimiter
 */
public class SlidingWindowLimiter implements RateLimiter {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLimiter.class);

    // ======================== 常量 ========================

    /** 窗口未使用的过期时间（秒），超过此时间未访问的窗口将被清理 */
    private static final long WINDOW_EXPIRE_SECONDS = 300;

    /** 清理任务执行周期（秒） */
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    // ======================== 配置属性 ========================

    /** 默认窗口大小（秒） */
    private final long defaultWindowSize;

    /** 默认窗口内最大允许的许可数 */
    private final long defaultMaxPermits;

    // ======================== 数据存储 ========================

    /** 每个 key 对应一个滑动窗口 */
    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    /** 定时清理过期窗口的调度器 */
    private final ScheduledExecutorService cleanupScheduler;

    // ======================== 构造方法 ========================

    /**
     * 构造滑动窗口限流器。
     *
     * @param defaultWindowSize 默认窗口大小（秒）
     * @param defaultMaxPermits 默认窗口内最大许可数
     */
    public SlidingWindowLimiter(long defaultWindowSize, long defaultMaxPermits) {
        this.defaultWindowSize = defaultWindowSize;
        this.defaultMaxPermits = defaultMaxPermits;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sliding-window-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
    }

    // ======================== RateLimiter 接口实现 ========================

    /**
     * 尝试获取指定数量的许可。
     *
     * <p>实现流程：</p>
     * <ol>
     *   <li>获取或创建 key 对应的滑动窗口</li>
     *   <li>移除窗口外（过期）的记录</li>
     *   <li>统计当前窗口内的总请求数</li>
     *   <li>判断是否有足够余量，有则记录新请求；无则拒绝</li>
     * </ol>
     *
     * @param key     限流唯一标识
     * @param permits 请求许可数
     * @return 限流结果
     */
    @Override
    public RateLimitResult tryAcquire(String key, int permits) {
        SlidingWindow window = windows.computeIfAbsent(key,
                k -> new SlidingWindow(defaultWindowSize, defaultMaxPermits));

        synchronized (window) {
            long now = System.currentTimeMillis();
            trimExpired(window, now);

            long currentCount = getCurrentCount(window);

            if (currentCount + permits <= window.maxPermits) {
                // 允许通过：记录新的 (timestamp, count) 对
                window.records.addLast(new long[]{now, permits});
                window.lastAccessTime = now;
                long remaining = window.maxPermits - currentCount - permits;
                log.debug("Permitted: key={}, permits={}, currentCount={}, remaining={}",
                        key, permits, currentCount + permits, remaining);
                return RateLimitResult.allow(remaining);
            } else {
                // 被拒绝：计算预估等待时间
                long waitTimeMs = estimateWaitTime(window, permits);
                window.lastAccessTime = now;
                log.debug("Rejected: key={}, permits={}, currentCount={}, maxPermits={}, waitMs={}",
                        key, permits, currentCount, window.maxPermits, waitTimeMs);
                return RateLimitResult.deny(waitTimeMs);
            }
        }
    }

    /**
     * 重置指定 key 的限流状态，清除所有记录。
     *
     * @param key 限流唯一标识
     */
    @Override
    public void reset(String key) {
        windows.remove(key);
        log.info("Sliding window reset: key={}", key);
    }

    /**
     * 获取指定 key 当前可用的许可数（只读查询）。
     *
     * @param key 限流唯一标识
     * @return 可用许可数
     */
    @Override
    public long getAvailablePermits(String key) {
        SlidingWindow window = windows.get(key);
        if (window == null) {
            return defaultMaxPermits;
        }
        synchronized (window) {
            long now = System.currentTimeMillis();
            trimExpired(window, now);
            long currentCount = getCurrentCount(window);
            return Math.max(0, window.maxPermits - currentCount);
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 移除窗口外（过期）的记录。
     *
     * <p>从队首开始遍历，移除所有 {@code timestamp < (now - windowSize * 1000)} 的记录。</p>
     *
     * @param window 滑动窗口
     * @param now    当前时间戳（毫秒）
     */
    private void trimExpired(SlidingWindow window, long now) {
        long cutoff = now - window.windowSize * 1000L;
        while (!window.records.isEmpty() && window.records.peekFirst()[0] < cutoff) {
            window.records.pollFirst();
        }
    }

    /**
     * 计算当前窗口内的总请求数。
     *
     * @param window 滑动窗口
     * @return 窗口内的总计数
     */
    private long getCurrentCount(SlidingWindow window) {
        long count = 0;
        for (long[] record : window.records) {
            count += record[1];
        }
        return count;
    }

    /**
     * 估算等待时间（毫秒）。
     *
     * <p>计算最早的记录将在何时过期，该时间即为预估的有许可可用的时间。</p>
     *
     * @param window  滑动窗口
     * @param permits 需要的许可数
     * @return 预估等待时间（毫秒）
     */
    private long estimateWaitTime(SlidingWindow window, int permits) {
        if (window.records.isEmpty()) {
            return 0;
        }
        // 需要等待到最早记录过期的时间
        long earliestTimestamp = window.records.peekFirst()[0];
        long now = System.currentTimeMillis();
        long expiresAt = earliestTimestamp + window.windowSize * 1000L;
        return Math.max(0, expiresAt - now);
    }

    /**
     * 启动定期清理过期窗口的后台任务。
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long expireThreshold = WINDOW_EXPIRE_SECONDS * 1000;

                windows.entrySet().removeIf(entry -> {
                    SlidingWindow window = entry.getValue();
                    synchronized (window) {
                        if (now - window.lastAccessTime > expireThreshold) {
                            log.debug("Removing stale window: key={}", entry.getKey());
                            return true;
                        }
                        return false;
                    }
                });
            } catch (Exception e) {
                log.error("Error during sliding window cleanup", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // ======================== 资源释放 ========================

    /**
     * 关闭清理调度器，释放资源。
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SlidingWindowLimiter cleanup scheduler shut down");
    }

    // ======================== 内部类：滑动窗口模型 ========================

    /**
     * 滑动窗口的数据模型。
     *
     * <p>每个限流 key 对应一个窗口实例，使用双端队列存储时间片记录。</p>
     */
    private static class SlidingWindow {

        /** 窗口大小（秒） */
        final long windowSize;

        /** 窗口内最大允许的许可数 */
        final long maxPermits;

        /**
         * 时间片记录的队列。
         *
         * <p>每条记录格式为 {@code [timestamp (ms), count]}，
         * 按时间戳升序排列，队首为最早记录。</p>
         */
        final Deque<long[]> records = new ArrayDeque<>();

        /** 上次访问时间戳（毫秒），用于判断窗口是否过期 */
        long lastAccessTime;

        /**
         * 创建滑动窗口。
         *
         * @param windowSize 窗口大小（秒）
         * @param maxPermits 最大许可数
         */
        SlidingWindow(long windowSize, long maxPermits) {
            this.windowSize = windowSize;
            this.maxPermits = maxPermits;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
