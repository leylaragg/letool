package com.github.leyland.letool.monitor.api;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 调用统计采集器（聚合引擎）.
 *
 * <p>为每个 API 路径维护滑动窗口统计，支持按时间窗口查询聚合数据和时间序列。
 * 采用分钟级分桶策略，每个桶独立记录请求计数、成功/失败数和耗时样本。</p>
 *
 * <h3>窗口机制</h3>
 * <p>以分钟为最小粒度分桶，查询时根据指定的窗口大小（分钟数）合并桶数据。
 * 默认保留最近 60 分钟的历史数据（可通过 {@code letool.monitor.api-stats.window-size} 配置）。</p>
 *
 * <h3>路径匹配</h3>
 * <p>支持两种匹配方式：
 * <ul>
 *   <li>精确匹配 —— 传入完整路径如 {@code /api/user/login}</li>
 *   <li>前缀匹配 —— 传入模式如 {@code /api/user/} 匹配所有以该路径开头的请求</li>
 * </ul></p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ApiStatsCollector {

    // ======================== 字段 ========================

    /** 按 API 路径存储的统计信息 */
    private final ConcurrentHashMap<String, ApiPathStats> statsByPath = new ConcurrentHashMap<>();

    /** 聚合器，用于跨桶计算 */
    private final ApiStatsAggregator aggregator = new ApiStatsAggregator();

    /** 最大窗口大小（分钟），超出后旧桶自动回收 */
    private final int maxWindowMinutes;

    // ======================== 构造方法 ========================

    /**
     * 创建 API 统计采集器.
     *
     * @param maxWindowMinutes 最大滑动窗口大小（分钟），超出该范围的旧数据将被丢弃
     */
    public ApiStatsCollector(int maxWindowMinutes) {
        this.maxWindowMinutes = maxWindowMinutes;
    }

    // ======================== 公共方法 ========================

    /**
     * 记录一次 API 请求.
     *
     * @param path       API 请求路径
     * @param method     HTTP 方法（GET / POST 等）
     * @param statusCode HTTP 状态码
     * @param durationMs 请求耗时（毫秒）
     */
    public void record(String path, String method, int statusCode, long durationMs) {
        ApiPathStats stats = statsByPath.computeIfAbsent(path,
                k -> new ApiPathStats(path, method, maxWindowMinutes));
        stats.record(statusCode, durationMs);
    }

    /**
     * 获取指定路径的统计摘要.
     *
     * <p>支持前缀匹配：如果 {@code pathPattern} 以 {@code /**} 结尾，
     * 则匹配所有以该前缀开头的路径并合并统计；否则进行精确匹配。</p>
     *
     * @param pathPattern   路径模式（精确路径或以 {@code /**} 结尾的前缀）
     * @param windowMinutes 统计窗口大小（分钟）
     * @return 聚合后的统计摘要；如果没有数据则返回空的摘要对象
     */
    public ApiStatsSummary getStats(String pathPattern, int windowMinutes) {
        List<ApiPathStats> matchedStats = matchPaths(pathPattern);
        if (matchedStats.isEmpty()) {
            return new ApiStatsSummary();
        }

        List<ApiStatsSummary> summaries = new ArrayList<>();
        for (ApiPathStats stats : matchedStats) {
            ApiStatsSummary summary = aggregator.aggregate(stats, windowMinutes);
            summaries.add(summary);
        }
        return aggregator.merge(summaries);
    }

    /**
     * 获取指定路径的时间序列统计.
     *
     * <p>将时间窗口按粒度切分为多个子窗口，分别计算每个子窗口的统计摘要。</p>
     *
     * @param pathPattern        路径模式
     * @param windowMinutes      总时间窗口（分钟）
     * @param granularityMinutes 时间粒度（分钟），即每个子窗口大小
     * @return 按时间排序的统计摘要列表，每个元素对应一个子窗口
     */
    public List<ApiStatsSummary> getTimeSeries(String pathPattern, int windowMinutes, int granularityMinutes) {
        List<ApiPathStats> matchedStats = matchPaths(pathPattern);
        if (matchedStats.isEmpty()) {
            return Collections.emptyList();
        }

        // 将所有匹配路径的桶按分钟合并
        Map<Long, StatsBucket> mergedBuckets = new TreeMap<>();
        long currentMinute = System.currentTimeMillis() / 60_000;

        for (ApiPathStats stats : matchedStats) {
            for (Map.Entry<Long, StatsBucket> entry : stats.getBuckets().entrySet()) {
                long minuteKey = entry.getKey();
                // 只取在窗口范围内的桶（epoch 分钟永远递增，差值始终有效）
                long age = currentMinute - minuteKey;
                if (age >= 0 && age <= windowMinutes) {
                    mergedBuckets.merge(minuteKey, entry.getValue(), (existing, incoming) -> {
                        existing.merge(incoming);
                        return existing;
                    });
                }
            }
        }

        // 按粒度分组聚合
        List<ApiStatsSummary> timeSeries = new ArrayList<>();
        if (mergedBuckets.isEmpty()) return timeSeries;

        long subStart = currentMinute - windowMinutes;
        for (int i = 0; i < windowMinutes; i += granularityMinutes) {
            long subWinStart = subStart + i;
            long subWinEnd = Math.min(subWinStart + granularityMinutes, currentMinute);

            StatsBucket subAgg = new StatsBucket();
            for (Map.Entry<Long, StatsBucket> entry : mergedBuckets.entrySet()) {
                if (entry.getKey() >= subWinStart && entry.getKey() < subWinEnd) {
                    subAgg.merge(entry.getValue());
                }
            }

            if (subAgg.getCount() == 0) continue;

            ApiStatsSummary summary = aggregator.buildSummary(pathPattern, "ALL", subAgg,
                    minuteToLocalDateTime(subWinStart),
                    minuteToLocalDateTime(subWinEnd));
            timeSeries.add(summary);
        }

        return timeSeries;
    }

    /**
     * 重置所有统计数据.
     */
    public void reset() {
        statsByPath.clear();
    }

    /**
     * 获取当前采集的所有路径.
     *
     * @return 路径集合
     */
    public Set<String> getTrackedPaths() {
        return Collections.unmodifiableSet(statsByPath.keySet());
    }

    // ======================== 内部方法 ========================

    /**
     * 根据路径模式匹配对应的 ApiPathStats 列表.
     *
     * @param pathPattern 路径模式
     * @return 匹配的统计对象列表
     */
    List<ApiPathStats> matchPaths(String pathPattern) {
        List<ApiPathStats> result = new ArrayList<>();

        if (pathPattern == null || pathPattern.isEmpty() || pathPattern.equals("/**")) {
            result.addAll(statsByPath.values());
            return result;
        }

        // 前缀匹配：以 /** 结尾
        if (pathPattern.endsWith("/**")) {
            String prefix = pathPattern.substring(0, pathPattern.length() - 3);
            for (Map.Entry<String, ApiPathStats> entry : statsByPath.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    result.add(entry.getValue());
                }
            }
        } else {
            // 精确匹配
            ApiPathStats stats = statsByPath.get(pathPattern);
            if (stats != null) result.add(stats);
        }

        return result;
    }

    /**
     * 将绝对 epoch 分钟转为 LocalDateTime.
     *
     * @param epochMinute 绝对 epoch 分钟（自 1970-01-01 以来的分钟数）
     * @return 对应的 LocalDateTime
     */
    private LocalDateTime minuteToLocalDateTime(long epochMinute) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMinute * 60_000),
                java.time.ZoneId.systemDefault());
    }

    // ======================== 内部类：ApiPathStats ========================

    /**
     * 单个 API 路径的分钟级分桶统计.
     *
     * <p>以当前时间的分钟数为键，每个桶独立记录该分钟内的请求计数、
     * 成功/失败分布和所有请求耗时。</p>
     */
    public static class ApiPathStats {

        /** API 路径 */
        private final String path;

        /** HTTP 方法 */
        private final String method;

        /** 绝对分钟编号（epochMinute） -> 统计桶，使用绝对时间戳避免午夜回绕泄漏 */
        private final ConcurrentHashMap<Long, StatsBucket> buckets = new ConcurrentHashMap<>();

        /** 最大窗口大小，用于回收过期桶 */
        private final int maxWindowMinutes;

        /**
         * 创建 API 路径统计.
         *
         * @param path             API 路径
         * @param method           HTTP 方法
         * @param maxWindowMinutes 最大窗口大小（分钟）
         */
        public ApiPathStats(String path, String method, int maxWindowMinutes) {
            this.path = path;
            this.method = method;
            this.maxWindowMinutes = maxWindowMinutes;
        }

        /**
         * 记录一次 API 请求.
         *
         * @param statusCode HTTP 状态码
         * @param durationMs 请求耗时（毫秒）
         */
        public void record(int statusCode, long durationMs) {
            // 使用绝对 epoch 分钟作为 key，避免午夜时分钟编号回绕导致旧桶无法清理
            long currentMinute = System.currentTimeMillis() / 60_000;
            StatsBucket bucket = buckets.computeIfAbsent(currentMinute, k -> new StatsBucket());
            bucket.record(statusCode, durationMs);

            // 清理超出窗口的过期桶
            if (buckets.size() > maxWindowMinutes * 2) {
                cleanup(currentMinute);
            }
        }

        /**
         * 清理过期的统计桶.
         *
         * @param currentMinute 当前分钟编号
         */
        private void cleanup(long currentMinute) {
            // epoch 分钟永远递增，差值比较不受午夜回绕影响
            buckets.entrySet().removeIf(entry ->
                    (currentMinute - entry.getKey()) > maxWindowMinutes);
        }

        // ---- Getter ----

        public String getPath() { return path; }
        public String getMethod() { return method; }
        public ConcurrentHashMap<Long, StatsBucket> getBuckets() { return buckets; }
    }

    // ======================== 内部类：StatsBucket ========================

    /**
     * 单分钟统计桶.
     *
     * <p>记录一分钟内的请求计数、成功/失败分布、所有耗时样本和错误细分。</p>
     */
    public static class StatsBucket {

        /** 总请求数 */
        private final AtomicLong count = new AtomicLong(0);

        /** 成功请求数（状态码 2xx） */
        private final AtomicLong successCount = new AtomicLong(0);

        /** 总耗时（毫秒） */
        private final AtomicLong totalDurationMs = new AtomicLong(0);

        /** 所有请求耗时样本 */
        private final ConcurrentLinkedDeque<Long> durations = new ConcurrentLinkedDeque<>();

        /** 错误细分：异常类名 -> 计数 */
        private final ConcurrentHashMap<String, AtomicLong> errorBreakdown = new ConcurrentHashMap<>();

        /**
         * 记录一次请求.
         *
         * @param statusCode HTTP 状态码
         * @param durationMs 请求耗时（毫秒）
         */
        public void record(int statusCode, long durationMs) {
            count.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            durations.addLast(durationMs);

            // 保留最多 5000 条耗时样本
            while (durations.size() > 5000) {
                durations.pollFirst();
            }

            if (statusCode >= 200 && statusCode < 300) {
                successCount.incrementAndGet();
            } else if (statusCode >= 500) {
                errorBreakdown.computeIfAbsent("HTTP_" + statusCode, k -> new AtomicLong(0)).incrementAndGet();
            } else if (statusCode >= 400) {
                errorBreakdown.computeIfAbsent("HTTP_" + statusCode, k -> new AtomicLong(0)).incrementAndGet();
            }
        }

        /**
         * 合并另一个桶的数据到当前桶.
         *
         * @param other 被合并的统计桶
         */
        public void merge(StatsBucket other) {
            count.addAndGet(other.count.get());
            successCount.addAndGet(other.successCount.get());
            totalDurationMs.addAndGet(other.totalDurationMs.get());
            durations.addAll(other.durations);

            for (Map.Entry<String, AtomicLong> entry : other.errorBreakdown.entrySet()) {
                errorBreakdown.computeIfAbsent(entry.getKey(), k -> new AtomicLong(0))
                        .addAndGet(entry.getValue().get());
            }
        }

        // ---- Getter ----

        public long getCount() { return count.get(); }
        public long getSuccessCount() { return successCount.get(); }
        public long getTotalDurationMs() { return totalDurationMs.get(); }
        public List<Long> getDurations() { return new ArrayList<>(durations); }
        public Map<String, Long> getErrorBreakdown() {
            Map<String, Long> map = new LinkedHashMap<>();
            for (Map.Entry<String, AtomicLong> entry : errorBreakdown.entrySet()) {
                map.put(entry.getKey(), entry.getValue().get());
            }
            return map;
        }
    }
}
