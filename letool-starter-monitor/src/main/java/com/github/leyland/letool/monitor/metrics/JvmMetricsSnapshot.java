package com.github.leyland.letool.monitor.metrics;

import java.time.LocalDateTime;

/**
 * JVM 指标快照 —— 不可变对象，封装单次 JVM 指标采集的完整数据.
 *
 * <p>所有字段均为只读，该类为纯数据载体，不包含任何业务逻辑，线程安全（不可变）。</p>
 *
 * <h3>包含的指标</h3>
 * <ul>
 *   <li>堆内存使用量 / 最大值</li>
 *   <li>非堆内存使用量</li>
 *   <li>线程数（活跃 + 守护）</li>
 *   <li>CPU 负载（0.0 ~ 1.0）</li>
 *   <li>进程运行时长（毫秒）</li>
 *   <li>GC 次数及总耗时</li>
 *   <li>采集时间戳</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JvmMetricsSnapshot {

    // ======================== 字段 ========================

    /** 堆内存已使用字节数 */
    private final long heapUsedBytes;

    /** 堆内存最大可用字节数 */
    private final long heapMaxBytes;

    /** 非堆内存已使用字节数 */
    private final long nonHeapUsedBytes;

    /** 活跃线程数 */
    private final int threadCount;

    /** 守护线程数 */
    private final int daemonThreadCount;

    /** CPU 负载（0.0 ~ 1.0），-1 表示无法获取 */
    private final double cpuLoad;

    /** 进程已运行时长（毫秒） */
    private final long uptimeMs;

    /** 累计 GC 次数 */
    private final long gcCount;

    /** 累计 GC 总耗时（毫秒） */
    private final long gcTimeMs;

    /** 指标采集时间戳 */
    private final LocalDateTime timestamp;

    // ======================== 构造方法 ========================

    /**
     * 构造 JVM 指标快照.
     *
     * @param heapUsedBytes     堆内存已使用字节数
     * @param heapMaxBytes      堆内存最大可用字节数
     * @param nonHeapUsedBytes  非堆内存已使用字节数
     * @param threadCount       活跃线程数
     * @param daemonThreadCount 守护线程数
     * @param cpuLoad           CPU 负载
     * @param uptimeMs          进程运行时长（毫秒）
     * @param gcCount           累计 GC 次数
     * @param gcTimeMs          累计 GC 总耗时（毫秒）
     */
    public JvmMetricsSnapshot(long heapUsedBytes, long heapMaxBytes, long nonHeapUsedBytes,
                              int threadCount, int daemonThreadCount, double cpuLoad,
                              long uptimeMs, long gcCount, long gcTimeMs) {
        this.heapUsedBytes = heapUsedBytes;
        this.heapMaxBytes = heapMaxBytes;
        this.nonHeapUsedBytes = nonHeapUsedBytes;
        this.threadCount = threadCount;
        this.daemonThreadCount = daemonThreadCount;
        this.cpuLoad = cpuLoad;
        this.uptimeMs = uptimeMs;
        this.gcCount = gcCount;
        this.gcTimeMs = gcTimeMs;
        this.timestamp = LocalDateTime.now();
    }

    // ======================== Getter ========================

    /** @return 堆内存已使用字节数 */
    public long getHeapUsedBytes() { return heapUsedBytes; }

    /** @return 堆内存最大可用字节数 */
    public long getHeapMaxBytes() { return heapMaxBytes; }

    /** @return 非堆内存已使用字节数 */
    public long getNonHeapUsedBytes() { return nonHeapUsedBytes; }

    /** @return 活跃线程数 */
    public int getThreadCount() { return threadCount; }

    /** @return 守护线程数 */
    public int getDaemonThreadCount() { return daemonThreadCount; }

    /** @return CPU 负载（0.0 ~ 1.0），-1 表示无法获取 */
    public double getCpuLoad() { return cpuLoad; }

    /** @return 进程已运行时长（毫秒） */
    public long getUptimeMs() { return uptimeMs; }

    /** @return 累计 GC 次数 */
    public long getGcCount() { return gcCount; }

    /** @return 累计 GC 总耗时（毫秒） */
    public long getGcTimeMs() { return gcTimeMs; }

    /** @return 指标采集时间戳 */
    public LocalDateTime getTimestamp() { return timestamp; }

    // ======================== 便捷计算方法 ========================

    /**
     * 计算堆内存使用率.
     *
     * @return 堆内存使用率（0.0 ~ 1.0），若堆最大值为 0 则返回 0
     */
    public double getHeapUsagePercent() {
        if (heapMaxBytes <= 0) return 0.0;
        return (double) heapUsedBytes / heapMaxBytes;
    }

    /**
     * 获取总线程数（活跃 + 守护）.
     *
     * @return 总线程数
     */
    public int getTotalThreadCount() {
        return threadCount + daemonThreadCount;
    }

    @Override
    public String toString() {
        return String.format(
                "JvmMetricsSnapshot{heap=%d/%d MB, nonHeap=%d MB, threads=%d(%d daemon), cpu=%.2f%%, uptime=%d s, gc=%d/%d ms, ts=%s}",
                heapUsedBytes / 1024 / 1024, heapMaxBytes / 1024 / 1024,
                nonHeapUsedBytes / 1024 / 1024,
                threadCount, daemonThreadCount,
                cpuLoad * 100, uptimeMs / 1000,
                gcCount, gcTimeMs, timestamp);
    }
}
