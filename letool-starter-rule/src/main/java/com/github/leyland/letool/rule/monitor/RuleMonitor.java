package com.github.leyland.letool.rule.monitor;

import com.github.leyland.letool.rule.model.RuleMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 规则引擎执行监控 —— 收集和聚合规则链的执行指标.
 *
 * <h3>收集指标</h3>
 * <ul>
 *   <li><b>全局维度</b>：总执行次数、成功次数、失败次数</li>
 *   <li><b>链维度</b>：每个规则链的执行次数、总耗时、最小/最大耗时</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link AtomicLong} 和 {@link LongAdder} 保证高并发下的数据一致性，
 * 无需加锁即可支持多线程同时记录.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RuleMonitor monitor = new RuleMonitor();
 *
 * // 记录执行
 * monitor.recordExecution("riskChain", 150, true);
 *
 * // 查询指标
 * RuleMetrics metrics = monitor.getMetrics();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleMetrics
 */
public class RuleMonitor {

    private static final Logger log = LoggerFactory.getLogger(RuleMonitor.class);

    // ======================== 全局计数器 ========================

    /** 总执行次数 */
    private final AtomicLong totalExecutions = new AtomicLong(0);

    /** 成功执行次数 */
    private final AtomicLong successCount = new AtomicLong(0);

    /** 失败执行次数 */
    private final AtomicLong failureCount = new AtomicLong(0);

    // ======================== 单链统计 ========================

    /** 规则链维度统计（线程安全） */
    private final ConcurrentHashMap<String, ChainStats> chainStats = new ConcurrentHashMap<>();

    /**
     * 记录一次规则链执行.
     *
     * @param chainName  规则链名称
     * @param durationMs 执行耗时（毫秒）
     * @param success    是否执行成功
     */
    public void recordExecution(String chainName, long durationMs, boolean success) {
        totalExecutions.incrementAndGet();

        if (success) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }

        // 更新链维度统计
        ChainStats stats = chainStats.computeIfAbsent(chainName, k -> new ChainStats(chainName));
        stats.record(durationMs, success);
    }

    /**
     * 获取聚合后的执行指标.
     *
     * @return 执行指标汇总
     */
    public RuleMetrics getMetrics() {
        RuleMetrics metrics = new RuleMetrics();

        long total = totalExecutions.get();
        long success = successCount.get();

        metrics.setTotalExecutions(total);
        metrics.setSuccessRate(total > 0 ? (double) success / total * 100.0 : 0);

        // 计算全局平均耗时
        long totalDuration = 0;
        long totalChainCount = 0;
        List<RuleMetrics.ChainStat> chainStatList = new ArrayList<>();

        for (ChainStats stats : chainStats.values()) {
            RuleMetrics.ChainStat cs = new RuleMetrics.ChainStat(
                    stats.getChainName(),
                    stats.getCount(),
                    stats.getAvgMs(),
                    stats.getMinMs(),
                    stats.getMaxMs(),
                    stats.getSuccessRate()
            );
            chainStatList.add(cs);

            totalDuration += stats.getTotalDuration();
            totalChainCount += stats.getCount();
        }

        metrics.setAvgDurationMs(totalChainCount > 0 ? totalDuration / totalChainCount : 0);
        metrics.setChainStats(chainStatList);

        return metrics;
    }

    /**
     * 重置所有统计指标.
     */
    public void reset() {
        totalExecutions.set(0);
        successCount.set(0);
        failureCount.set(0);
        chainStats.clear();
        log.info("规则引擎监控指标已重置");
    }

    // ======================== getter ========================

    /**
     * 获取总执行次数.
     *
     * @return 总执行次数
     */
    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    /**
     * 获取成功执行次数.
     *
     * @return 成功次数
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取失败执行次数.
     *
     * @return 失败次数
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    // ======================== 内部类：单链统计累加器 ========================

    /**
     * 单个规则链的统计累加器 —— 支持并发记录和高低水位标记.
     */
    private static class ChainStats {

        /** 规则链名称 */
        private final String chainName;

        /** 执行次数 */
        private final LongAdder count = new LongAdder();

        /** 成功次数 */
        private final LongAdder successCount = new LongAdder();

        /** 总耗时（毫秒） */
        private final LongAdder totalDuration = new LongAdder();

        /** 最小耗时（毫秒），初始为 Long.MAX_VALUE */
        private volatile long minMs = Long.MAX_VALUE;

        /** 最大耗时（毫秒），初始为 0 */
        private volatile long maxMs = 0;

        /**
         * 创建链统计累加器.
         *
         * @param chainName 规则链名称
         */
        ChainStats(String chainName) {
            this.chainName = chainName;
        }

        /**
         * 记录一次执行.
         *
         * @param durationMs 执行耗时
         * @param success    是否成功
         */
        void record(long durationMs, boolean success) {
            count.increment();
            totalDuration.add(durationMs);
            if (success) {
                successCount.increment();
            }

            // 更新最小耗时（CAS 风格）
            long currentMin;
            do {
                currentMin = minMs;
                if (durationMs >= currentMin) {
                    break;
                }
            } while (!updateMin(durationMs));

            // 更新最大耗时
            long currentMax;
            do {
                currentMax = maxMs;
                if (durationMs <= currentMax) {
                    break;
                }
            } while (!updateMax(durationMs));
        }

        /**
         * CAS 更新最小耗时.
         */
        private synchronized boolean updateMin(long newMin) {
            if (newMin < minMs) {
                minMs = newMin;
                return true;
            }
            return false;
        }

        /**
         * CAS 更新最大耗时.
         */
        private synchronized boolean updateMax(long newMax) {
            if (newMax > maxMs) {
                maxMs = newMax;
                return true;
            }
            return false;
        }

        // ======================== getter ========================

        String getChainName() { return chainName; }
        long getCount() { return count.sum(); }
        long getTotalDuration() { return totalDuration.sum(); }

        long getAvgMs() {
            long c = count.sum();
            return c > 0 ? totalDuration.sum() / c : 0;
        }

        long getMinMs() {
            long m = minMs;
            return m == Long.MAX_VALUE ? 0 : m;
        }

        long getMaxMs() { return maxMs; }

        double getSuccessRate() {
            long c = count.sum();
            return c > 0 ? (double) successCount.sum() / c * 100.0 : 0;
        }
    }
}
