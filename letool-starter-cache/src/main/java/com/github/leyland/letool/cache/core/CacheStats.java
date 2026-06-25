package com.github.leyland.letool.cache.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存统计 —— 记录 L1/L2 命中、未命中、加载次数等指标.
 */
public class CacheStats {

    private final AtomicLong l1HitCount = new AtomicLong();
    private final AtomicLong l2HitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong loadCount = new AtomicLong();
    private final AtomicLong loadSuccessCount = new AtomicLong();
    private final AtomicLong loadFailureCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong l2DegradedCount = new AtomicLong();

    public void recordL1Hit() { l1HitCount.incrementAndGet(); }
    public void recordL2Hit() { l2HitCount.incrementAndGet(); }
    public void recordMiss() { missCount.incrementAndGet(); }
    public void recordLoad() { loadCount.incrementAndGet(); }
    public void recordLoadSuccess() { loadSuccessCount.incrementAndGet(); }
    public void recordLoadFailure() { loadFailureCount.incrementAndGet(); }
    public void recordEviction() { evictionCount.incrementAndGet(); }
    public void recordL2Degraded() { l2DegradedCount.incrementAndGet(); }

    public long getL1HitCount() { return l1HitCount.get(); }
    public long getL2HitCount() { return l2HitCount.get(); }
    public long getMissCount() { return missCount.get(); }
    public long getLoadCount() { return loadCount.get(); }
    public long getLoadSuccessCount() { return loadSuccessCount.get(); }
    public long getLoadFailureCount() { return loadFailureCount.get(); }
    public long getEvictionCount() { return evictionCount.get(); }
    public long getL2DegradedCount() { return l2DegradedCount.get(); }

    public long getTotalRequests() {
        return l1HitCount.get() + l2HitCount.get() + missCount.get();
    }

    public double getL1HitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0 : (double) l1HitCount.get() / total;
    }

    public double getL2HitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0 : (double) l2HitCount.get() / total;
    }

    public double getTotalHitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0 : (double) (l1HitCount.get() + l2HitCount.get()) / total;
    }
}
