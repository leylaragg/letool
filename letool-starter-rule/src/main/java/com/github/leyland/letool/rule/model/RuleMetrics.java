package com.github.leyland.letool.rule.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎执行指标汇总模型.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>汇总全局执行统计：总执行次数、成功率、平均耗时</li>
 *   <li>按规则链维度展示各链的执行统计明细</li>
 *   <li>用于 REST API 的 {@code GET /api/rule/metrics} 接口返回</li>
 * </ul>
 *
 * <h3>数据来源</h3>
 * <p>由 {@link com.github.leyland.letool.rule.monitor.RuleMonitor RuleMonitor}
 * 收集并聚合后生成此对象.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.rule.monitor.RuleMonitor
 */
public class RuleMetrics {

    /** 全局总执行次数 */
    private long totalExecutions;

    /** 全局成功率（0.0 ~ 100.0） */
    private double successRate;

    /** 全局平均耗时（毫秒） */
    private long avgDurationMs;

    /** 各规则链的统计明细 */
    private List<ChainStat> chainStats = new ArrayList<>();

    // ======================== getter / setter ========================

    public long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public long getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(long avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

    public List<ChainStat> getChainStats() {
        return chainStats;
    }

    public void setChainStats(List<ChainStat> chainStats) {
        this.chainStats = chainStats;
    }

    // ======================== 内部类：单个规则链统计 ========================

    /**
     * 单个规则链的执行统计信息.
     *
     * <p>包含链名称、执行次数、平均/最小/最大耗时、成功率等核心指标.</p>
     */
    public static class ChainStat {

        /** 规则链名称 */
        private String chainName;

        /** 执行次数 */
        private long count;

        /** 平均耗时（毫秒） */
        private long avgMs;

        /** 最小耗时（毫秒） */
        private long minMs;

        /** 最大耗时（毫秒） */
        private long maxMs;

        /** 成功率（0.0 ~ 100.0） */
        private double successRate;

        // ======================== 构造方法 ========================

        /**
         * 创建空的链统计对象.
         */
        public ChainStat() {
        }

        /**
         * 创建带基本参数的链统计对象.
         *
         * @param chainName  规则链名称
         * @param count      执行次数
         * @param avgMs      平均耗时
         * @param minMs      最小耗时
         * @param maxMs      最大耗时
         * @param successRate 成功率
         */
        public ChainStat(String chainName, long count, long avgMs, long minMs, long maxMs, double successRate) {
            this.chainName = chainName;
            this.count = count;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.successRate = successRate;
        }

        // ======================== getter / setter ========================

        public String getChainName() {
            return chainName;
        }

        public void setChainName(String chainName) {
            this.chainName = chainName;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public long getAvgMs() {
            return avgMs;
        }

        public void setAvgMs(long avgMs) {
            this.avgMs = avgMs;
        }

        public long getMinMs() {
            return minMs;
        }

        public void setMinMs(long minMs) {
            this.minMs = minMs;
        }

        public long getMaxMs() {
            return maxMs;
        }

        public void setMaxMs(long maxMs) {
            this.maxMs = maxMs;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
        }
    }
}
