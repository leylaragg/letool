package com.github.leyland.letool.ratelimiter.algorithm;

import com.github.leyland.letool.ratelimiter.core.RateLimiter;
import com.github.leyland.letool.ratelimiter.core.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 令牌桶（Token Bucket）限流器实现。
 *
 * <p>令牌桶算法是最常用的限流算法之一，核心思想是：</p>
 * <ol>
 *   <li>以固定速率向桶中补充令牌</li>
 *   <li>请求到达时需从桶中获取令牌，令牌不足则拒绝</li>
 *   <li>桶有最大容量，允许短时间的突发流量（桶中积攒的令牌可一次性消耗）</li>
 * </ol>
 *
 * <h3>算法公式</h3>
 * <pre>{@code
 * elapsed = now - lastRefillTime          // 距上次补充的时间（秒）
 * tokens = min(capacity, tokens + elapsed * refillRate)  // 补充令牌
 * if tokens >= permits:
 *     tokens -= permits                   // 扣减令牌，允许通过
 * else:
 *     waitTimeMs = (permits - tokens) / refillRate * 1000  // 预估等待时间
 * }</pre>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@code synchronized} 对每个 key 的桶对象加锁，保证同一 key 的并发安全。
 * 不同 key 的桶互不影响，整体并发性能良好。</p>
 *
 * <h3>内存管理</h3>
 * <p>通过 {@link ScheduledExecutorService} 定期清理长时间未使用的桶，
 * 防止内存泄漏。清理周期为 60 秒，超过 300 秒未访问的桶将被移除。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimiter
 * @see SlidingWindowLimiter
 */
public class TokenBucketLimiter implements RateLimiter {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(TokenBucketLimiter.class);

    // ======================== 常量 ========================

    /** 桶未使用的过期时间（秒），超过此时间未访问的桶将被清理 */
    private static final long BUCKET_EXPIRE_SECONDS = 300;

    /** 清理任务执行周期（秒） */
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    // ======================== 配置属性 ========================

    /** 桶的默认最大容量（令牌数） */
    private final long defaultCapacity;

    /** 默认每秒补充的令牌数（稳态 QPS） */
    private final double defaultRefillRate;

    // ======================== 数据存储 ========================

    /** 每个 key 对应一个令牌桶，线程安全的 map */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** 定时清理过期桶的调度器 */
    private final ScheduledExecutorService cleanupScheduler;

    // ======================== 构造方法 ========================

    /**
     * 构造令牌桶限流器。
     *
     * @param defaultCapacity   默认桶容量（最大令牌数）
     * @param defaultRefillRate 默认每秒补充令牌数
     */
    public TokenBucketLimiter(long defaultCapacity, double defaultRefillRate) {
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRate = defaultRefillRate;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-bucket-cleanup");
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
     *   <li>获取或创建 key 对应的令牌桶</li>
     *   <li>根据时间差补充令牌</li>
     *   <li>检查令牌是否足够，不够则返回拒绝结果，足够则扣减</li>
     * </ol>
     *
     * @param key     限流唯一标识
     * @param permits 请求许可数
     * @return 限流结果
     */
    @Override
    public RateLimitResult tryAcquire(String key, int permits) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(defaultCapacity, defaultRefillRate));

        synchronized (bucket) {
            refill(bucket);

            if (bucket.tokens >= permits) {
                bucket.tokens -= permits;
                bucket.lastAccessTime = System.currentTimeMillis();
                log.debug("Permitted: key={}, permits={}, remaining={}", key, permits, (long) bucket.tokens);
                return RateLimitResult.allow((long) bucket.tokens);
            } else {
                // 计算预估等待时间（毫秒）
                double deficit = permits - bucket.tokens;
                long waitTimeMs = (long) ((deficit / bucket.refillRate) * 1000);
                bucket.lastAccessTime = System.currentTimeMillis();
                log.debug("Rejected: key={}, permits={}, tokens={}, waitMs={}", key, permits, (long) bucket.tokens, waitTimeMs);
                return RateLimitResult.deny(waitTimeMs);
            }
        }
    }

    /**
     * 重置指定 key 的限流状态，恢复到满令牌状态。
     *
     * @param key 限流唯一标识
     */
    @Override
    public void reset(String key) {
        buckets.remove(key);
        log.info("Token bucket reset: key={}", key);
    }

    /**
     * 获取指定 key 当前可用的令牌数（只读查询）。
     *
     * @param key 限流唯一标识
     * @return 可用令牌数，如果 key 不存在则返回默认容量
     */
    @Override
    public long getAvailablePermits(String key) {
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            return defaultCapacity;
        }
        synchronized (bucket) {
            refill(bucket);
            return (long) bucket.tokens;
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 根据时间差补充令牌。
     *
     * <p>计算自上次补充以来的时间差，按 {@code refillRate} 补充令牌，
     * 总量不超过 {@code capacity}。</p>
     *
     * @param bucket 要补充的令牌桶
     */
    private void refill(TokenBucket bucket) {
        long now = System.currentTimeMillis();
        long elapsedMs = now - bucket.lastRefillTime;
        if (elapsedMs <= 0) {
            return;
        }

        double elapsedSeconds = elapsedMs / 1000.0;
        double tokensToAdd = elapsedSeconds * bucket.refillRate;
        bucket.tokens = Math.min(bucket.capacity, bucket.tokens + tokensToAdd);
        bucket.lastRefillTime = now;
    }

    /**
     * 启动定期清理过期桶的后台任务。
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long expireThreshold = BUCKET_EXPIRE_SECONDS * 1000;

                buckets.entrySet().removeIf(entry -> {
                    TokenBucket bucket = entry.getValue();
                    synchronized (bucket) {
                        if (now - bucket.lastAccessTime > expireThreshold) {
                            log.debug("Removing stale bucket: key={}", entry.getKey());
                            return true;
                        }
                        return false;
                    }
                });
            } catch (Exception e) {
                log.error("Error during token bucket cleanup", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // ======================== 资源释放 ========================

    /**
     * 关闭清理调度器，释放资源。
     *
     * <p>通常在应用关闭时由 Spring 容器自动调用（若注册了 destroy 方法）。</p>
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
        log.info("TokenBucketLimiter cleanup scheduler shut down");
    }

    // ======================== 内部类：令牌桶模型 ========================

    /**
     * 令牌桶的数据模型。
     *
     * <p>每个限流 key 对应一个桶实例，记录该 key 当前的令牌数、配置参数和访问时间。</p>
     */
    private static class TokenBucket {

        /** 桶的容量（最大令牌数） */
        final long capacity;

        /** 当前令牌数（double 类型以支持精确计算） */
        double tokens;

        /** 每秒补充的令牌数 */
        final double refillRate;

        /** 上次补充令牌的时间戳（毫秒） */
        long lastRefillTime;

        /** 上次访问时间戳（毫秒），用于判断桶是否过期 */
        long lastAccessTime;

        /**
         * 创建令牌桶。
         *
         * @param capacity   桶容量
         * @param refillRate 补充速率
         */
        TokenBucket(long capacity, double refillRate) {
            this.capacity = capacity;
            this.tokens = capacity;  // 初始时为满桶
            this.refillRate = refillRate;
            long now = System.currentTimeMillis();
            this.lastRefillTime = now;
            this.lastAccessTime = now;
        }
    }
}
