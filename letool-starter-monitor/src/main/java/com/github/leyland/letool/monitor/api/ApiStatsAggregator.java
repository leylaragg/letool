package com.github.leyland.letool.monitor.api;

import java.time.LocalDateTime;
import java.util.*;

/**
 * API 统计聚合器.
 *
 * <p>负责将 {@link ApiStatsCollector.ApiPathStats} 的分钟级桶数据聚合为指定窗口的
 * {@link ApiStatsSummary}，包括计算百分位数、错误分布等。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ApiStatsAggregator {

    // ======================== 公共方法 ========================

    /**
     * 将指定路径的统计数据聚合为指定时间窗口的摘要.
     *
     * <p>只聚合最近 {@code minutes} 分钟内的桶数据，忽略过期桶。</p>
     *
     * @param stats   路径统计数据（包含所有桶）
     * @param minutes 统计窗口大小（分钟）
     * @return 聚合后的 API 统计摘要；无数据时返回空摘要
     */
    public ApiStatsSummary aggregate(ApiStatsCollector.ApiPathStats stats, int minutes) {
        LocalDateTime now = LocalDateTime.now();
        int currentMinute = (int) (now.toLocalTime().toSecondOfDay() / 60);

        // 合并窗口内的所有桶
        ApiStatsCollector.StatsBucket mergedBucket = new ApiStatsCollector.StatsBucket();
        for (Map.Entry<Integer, ApiStatsCollector.StatsBucket> entry : stats.getBuckets().entrySet()) {
            int minuteKey = entry.getKey();
            if ((currentMinute - minuteKey) >= 0 && (currentMinute - minuteKey) <= minutes) {
                mergedBucket.merge(entry.getValue());
            }
        }

        return buildSummary(stats.getPath(), stats.getMethod(), mergedBucket,
                now.minusMinutes(minutes), now);
    }

    /**
     * 聚合多个路径的统计为按路径分组的摘要列表.
     *
     * @param pathStatsList 多个路径的统计数据
     * @return 路径 -> 该路径的摘要列表（通常每路径一个摘要对象）
     */
    public Map<String, List<ApiStatsSummary>> aggregateByPath(List<ApiStatsCollector.ApiPathStats> pathStatsList) {
        Map<String, List<ApiStatsSummary>> result = new LinkedHashMap<>();
        for (ApiStatsCollector.ApiPathStats stats : pathStatsList) {
            ApiStatsSummary summary = aggregate(stats, 60); // 默认最近 60 分钟
            result.computeIfAbsent(stats.getPath(), k -> new ArrayList<>()).add(summary);
        }
        return result;
    }

    /**
     * 合并多个摘要为单个摘要.
     *
     * <p>用于将多个匹配路径的统计结果合并为一个整体摘要。
     * 合并规则：请求数和成功数求和，时间取范围，百分位数基于加权平均。</p>
     *
     * @param summaries 待合并的摘要列表
     * @return 合并后的摘要；列表为空或 {@code null} 时返回空摘要
     */
    public ApiStatsSummary merge(List<ApiStatsSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return new ApiStatsSummary();
        }
        if (summaries.size() == 1) {
            return summaries.get(0);
        }

        long totalRequests = 0;
        long successCount = 0;
        double totalAvgWeighted = 0;
        long minMs = Long.MAX_VALUE;
        long maxMs = Long.MIN_VALUE;
        LocalDateTime windowStart = null;
        LocalDateTime windowEnd = null;
        Map<String, Long> mergedErrors = new LinkedHashMap<>();

        // 收集所有耗时用于重算百分位数
        List<Long> allDurations = new ArrayList<>();

        for (ApiStatsSummary s : summaries) {
            totalRequests += s.getTotalRequests();
            successCount += s.getSuccessCount();
            totalAvgWeighted += s.getAvgResponseTimeMs() * s.getTotalRequests();

            if (s.getMinMs() < minMs) minMs = s.getMinMs();
            if (s.getMaxMs() > maxMs) maxMs = s.getMaxMs();

            if (windowStart == null || (s.getWindowStart() != null && s.getWindowStart().isBefore(windowStart))) {
                windowStart = s.getWindowStart();
            }
            if (windowEnd == null || (s.getWindowEnd() != null && s.getWindowEnd().isAfter(windowEnd))) {
                windowEnd = s.getWindowEnd();
            }

            for (Map.Entry<String, Long> entry : s.getErrorBreakdown().entrySet()) {
                mergedErrors.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        double avgMs = totalRequests > 0 ? totalAvgWeighted / totalRequests : 0;
        if (minMs == Long.MAX_VALUE) minMs = 0;
        if (maxMs == Long.MIN_VALUE) maxMs = 0;
        double successRate = totalRequests > 0 ? (double) successCount / totalRequests : 0;

        // 对合并后的耗时样本排序用于百分位数计算
        Collections.sort(allDurations);

        return new ApiStatsSummary(
                "MERGED", "ALL", totalRequests, successCount, successRate, avgMs,
                minMs, maxMs,
                percentile(allDurations, 50),
                percentile(allDurations, 90),
                percentile(allDurations, 95),
                percentile(allDurations, 99),
                mergedErrors,
                windowStart, windowEnd
        );
    }

    // ======================== 内部方法 ========================

    /**
     * 从 StatsBucket 构建 ApiStatsSummary（包级可见，供 ApiStatsCollector 调用）.
     *
     * @param path    API 路径
     * @param method  HTTP 方法
     * @param bucket  统计桶
     * @param start   窗口起始时间
     * @param end     窗口结束时间
     * @return API 统计摘要
     */
    ApiStatsSummary buildSummary(String path, String method,
                                          ApiStatsCollector.StatsBucket bucket,
                                          LocalDateTime start, LocalDateTime end) {
        List<Long> sortedDurations = new ArrayList<>(bucket.getDurations());
        Collections.sort(sortedDurations);

        long total = bucket.getCount();
        long success = bucket.getSuccessCount();
        double successRate = total > 0 ? (double) success / total : 0.0;
        double avgMs = total > 0 ? bucket.getTotalDurationMs() / (double) total : 0.0;
        long minMs = sortedDurations.isEmpty() ? 0 : sortedDurations.get(0);
        long maxMs = sortedDurations.isEmpty() ? 0 : sortedDurations.get(sortedDurations.size() - 1);

        return new ApiStatsSummary(
                path, method, total, success, successRate, avgMs, minMs, maxMs,
                percentile(sortedDurations, 50),
                percentile(sortedDurations, 90),
                percentile(sortedDurations, 95),
                percentile(sortedDurations, 99),
                new LinkedHashMap<>(bucket.getErrorBreakdown()),
                start, end
        );
    }

    /**
     * 计算百分位数.
     *
     * @param sorted     已排序的数值列表
     * @param percentile 百分位（0-100）
     * @return 对应百分位的值，列表为空时返回 0
     */
    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
