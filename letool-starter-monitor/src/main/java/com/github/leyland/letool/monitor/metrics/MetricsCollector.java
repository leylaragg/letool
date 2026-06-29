package com.github.leyland.letool.monitor.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer 风格的指标收集器，提供基于内存的计数器与计时器功能.
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>计数器 Counter</b> —— 基于 {@link AtomicLong}，支持原子递增和查询</li>
 *   <li><b>计时器 Timer</b> —— 基于滑动时间窗口，记录每次耗时并计算 avg / min / max</li>
 *   <li><b>快照导出</b> —— {@link #getAllMetrics()} 返回当前所有指标的 Map 视图</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <p>本类是 Micrometer 的轻量替代实现，不依赖外部监控库。
 * 所有指标数据存储在内存中，适用于中小规模应用场景。
 * 若需要对接 Prometheus，可在此层之上封装适配器。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * metricsCollector.increment("order.created");
 * metricsCollector.recordTime("order.process", 150);
 * long count = metricsCollector.getCounterValue("order.created");
 * MetricsCollector.TimerStats stats = metricsCollector.getTimerStats("order.process");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MetricsCollector {

    // ======================== 指标存储 ========================

    /** 计数器注册表 —— key 为指标名称，value 为原子计数器 */
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /** 计时器注册表 —— key 为指标名称，value 为滑动窗口计时器 */
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    // ======================== Counter 操作 ========================

    /**
     * 获取或创建指定名称的计数器.
     *
     * @param name 指标名称，建议使用点号分隔的命名规范（如 "order.created"）
     * @return 原子计数器实例，保证同一 name 返回相同实例
     */
    public AtomicLong counter(String name) {
        return counters.computeIfAbsent(name, k -> new AtomicLong(0));
    }

    /**
     * 递增指定名称的计数器，并返回递增后的值.
     *
     * @param name 指标名称
     * @return 递增后的计数值
     */
    public long increment(String name) {
        return counter(name).incrementAndGet();
    }

    /**
     * 获取指定名称计数器的当前值.
     *
     * @param name 指标名称
     * @return 当前计数值，若该计数器不存在则返回 0
     */
    public long getCounterValue(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取所有已注册的计数器名称.
     *
     * @return 计数器名称的不可变集合
     */
    public Set<String> getCounterNames() {
        return Collections.unmodifiableSet(counters.keySet());
    }

    // ======================== Timer 操作 ========================

    /**
     * 获取或创建指定名称的计时器.
     *
     * @param name 指标名称，建议使用点号分隔的命名规范（如 "order.process"）
     * @return 计时器实例，保证同一 name 返回相同实例
     */
    public Timer timer(String name) {
        return timers.computeIfAbsent(name, k -> new Timer(name));
    }

    /**
     * 记录一次耗时到指定计时器.
     *
     * @param name   指标名称
     * @param millis 耗时（毫秒）
     */
    public void recordTime(String name, long millis) {
        timer(name).record(millis);
    }

    /**
     * 获取指定名称计时器的统计信息.
     *
     * @param name 指标名称
     * @return 计时器统计快照（count / avg / min / max），若不存在则返回空统计
     */
    public TimerStats getTimerStats(String name) {
        Timer timer = timers.get(name);
        return timer != null ? timer.stats() : new TimerStats(0, 0, 0, 0);
    }

    /**
     * 获取所有已注册的计时器名称.
     *
     * @return 计时器名称的不可变集合
     */
    public Set<String> getTimerNames() {
        return Collections.unmodifiableSet(timers.keySet());
    }

    // ======================== 全量指标导出 ========================

    /**
     * 获取当前所有指标的快照，包含计数器值和计时器统计信息.
     *
     * @return 不可修改的指标快照 Map，key 为指标名称，value 为 Long 或 TimerStats
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> snapshot = new HashMap<>();

        // 收集所有计数器快照
        counters.forEach((name, counter) -> snapshot.put(name, counter.get()));

        // 收集所有计时器快照
        timers.forEach((name, timer) -> snapshot.put(name, timer.stats()));

        return Collections.unmodifiableMap(snapshot);
    }

    // ======================== 内部类：计时器 ========================

    /**
     * 基于滑动时间窗口的计时器，记录每次调用的耗时数据.
     *
     * <p>内部使用 {@link ConcurrentLinkedDeque} 存储最近 N 次耗时记录，
     * 每次调用 {@link #stats()} 时实时计算 avg / min / max。</p>
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Timer {

        /** 计时器名称 */
        private final String name;

        /** 最大窗口大小，超出时移除最早的记录 */
        private static final int MAX_WINDOW_SIZE = 10_000;

        /** 耗时记录双端队列 */
        private final ConcurrentLinkedDeque<Long> records = new ConcurrentLinkedDeque<>();

        /** 总调用次数（不受窗口滑动影响） */
        private final AtomicLong totalCount = new AtomicLong(0);

        /**
         * 构造计时器.
         *
         * @param name 计时器名称
         */
        public Timer(String name) {
            this.name = name;
        }

        /**
         * 记录一次耗时.
         *
         * @param millis 耗时毫秒数
         */
        public void record(long millis) {
            records.addLast(millis);
            totalCount.incrementAndGet();
            // 超过窗口大小时移除最早记录
            while (records.size() > MAX_WINDOW_SIZE) {
                records.pollFirst();
            }
        }

        /**
         * 计算当前时间窗口内的统计信息.
         *
         * @return 计时器统计快照
         */
        public TimerStats stats() {
            if (records.isEmpty()) {
                return new TimerStats(0, 0, 0, 0);
            }
            long sum = 0;
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            int count = 0;
            for (Long record : records) {
                sum += record;
                if (record < min) min = record;
                if (record > max) max = record;
                count++;
            }
            double avg = (double) sum / count;
            return new TimerStats(count, avg, min, max);
        }

        /**
         * 获取计时器名称.
         *
         * @return 计时器名称
         */
        public String getName() { return name; }

        /**
         * 获取总调用次数（包含已被滑动窗口淘汰的记录）.
         *
         * @return 总调用次数
         */
        public long getTotalCount() { return totalCount.get(); }
    }

    // ======================== 内部类：计时器统计快照 ========================

    /**
     * 计时器统计快照，包含调用次数、平均耗时、最小耗时、最大耗时.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class TimerStats {

        /** 调用次数 */
        private final long count;

        /** 平均耗时（毫秒） */
        private final double avgMs;

        /** 最小耗时（毫秒） */
        private final long minMs;

        /** 最大耗时（毫秒） */
        private final long maxMs;

        /**
         * 构造计时器统计快照.
         *
         * @param count 调用次数
         * @param avgMs 平均耗时（毫秒）
         * @param minMs 最小耗时（毫秒）
         * @param maxMs 最大耗时（毫秒）
         */
        public TimerStats(long count, double avgMs, long minMs, long maxMs) {
            this.count = count;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
        }

        public long getCount() { return count; }

        public double getAvgMs() { return avgMs; }

        public long getMinMs() { return minMs; }

        public long getMaxMs() { return maxMs; }

        @Override
        public String toString() {
            return String.format("TimerStats{count=%d, avg=%.2fms, min=%dms, max=%dms}", count, avgMs, minMs, maxMs);
        }
    }
}
