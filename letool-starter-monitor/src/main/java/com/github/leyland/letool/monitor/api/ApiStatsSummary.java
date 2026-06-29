package com.github.leyland.letool.monitor.api;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * API 统计汇总模型.
 *
 * <p>对一个 API 路径在指定时间窗口内的聚合统计数据，包括请求量、
 * 成功率、响应延迟百分位数、以及按异常类型的错误分布。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ApiStatsSummary {

    // ======================== 字段 ========================

    /** API 路径 */
    private String path;

    /** HTTP 方法 */
    private String method;

    /** 总请求次数 */
    private long totalRequests;

    /** 成功请求次数 */
    private long successCount;

    /** 成功率（0.0 ~ 1.0） */
    private double successRate;

    /** 平均响应时间（毫秒） */
    private double avgResponseTimeMs;

    /** 最小响应时间（毫秒） */
    private long minMs;

    /** 最大响应时间（毫秒） */
    private long maxMs;

    /** P50 百分位响应时间（毫秒） */
    private long p50Ms;

    /** P90 百分位响应时间（毫秒） */
    private long p90Ms;

    /** P95 百分位响应时间（毫秒） */
    private long p95Ms;

    /** P99 百分位响应时间（毫秒） */
    private long p99Ms;

    /** 错误细分 —— 异常类名 -> 发生次数 */
    private Map<String, Long> errorBreakdown = new HashMap<>();

    /** 统计窗口起始时间 */
    private LocalDateTime windowStart;

    /** 统计窗口结束时间 */
    private LocalDateTime windowEnd;

    // ======================== 构造方法 ========================

    /** 创建空的 API 统计汇总. */
    public ApiStatsSummary() {
    }

    /**
     * 创建 API 统计汇总并初始化所有字段.
     *
     * @param path              API 路径
     * @param method            HTTP 方法
     * @param totalRequests     总请求次数
     * @param successCount      成功请求次数
     * @param successRate       成功率
     * @param avgResponseTimeMs 平均响应时间（毫秒）
     * @param minMs             最小响应时间（毫秒）
     * @param maxMs             最大响应时间（毫秒）
     * @param p50Ms            P50 响应时间（毫秒）
     * @param p90Ms            P90 响应时间（毫秒）
     * @param p95Ms            P95 响应时间（毫秒）
     * @param p99Ms            P99 响应时间（毫秒）
     * @param errorBreakdown    错误细分映射
     * @param windowStart       窗口起始时间
     * @param windowEnd         窗口结束时间
     */
    public ApiStatsSummary(String path, String method, long totalRequests, long successCount,
                           double successRate, double avgResponseTimeMs, long minMs, long maxMs,
                           long p50Ms, long p90Ms, long p95Ms, long p99Ms,
                           Map<String, Long> errorBreakdown,
                           LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.path = path;
        this.method = method;
        this.totalRequests = totalRequests;
        this.successCount = successCount;
        this.successRate = successRate;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.p50Ms = p50Ms;
        this.p90Ms = p90Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.errorBreakdown = errorBreakdown != null ? errorBreakdown : new HashMap<>();
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    // ======================== Getter / Setter ========================

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }

    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public long getMinMs() { return minMs; }
    public void setMinMs(long minMs) { this.minMs = minMs; }

    public long getMaxMs() { return maxMs; }
    public void setMaxMs(long maxMs) { this.maxMs = maxMs; }

    public long getP50Ms() { return p50Ms; }
    public void setP50Ms(long p50Ms) { this.p50Ms = p50Ms; }

    public long getP90Ms() { return p90Ms; }
    public void setP90Ms(long p90Ms) { this.p90Ms = p90Ms; }

    public long getP95Ms() { return p95Ms; }
    public void setP95Ms(long p95Ms) { this.p95Ms = p95Ms; }

    public long getP99Ms() { return p99Ms; }
    public void setP99Ms(long p99Ms) { this.p99Ms = p99Ms; }

    public Map<String, Long> getErrorBreakdown() { return errorBreakdown; }
    public void setErrorBreakdown(Map<String, Long> errorBreakdown) { this.errorBreakdown = errorBreakdown; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    // ======================== 便捷方法 ========================

    /**
     * 获取总错误次数（非 2xx 状态码的请求数）.
     *
     * @return 总错误次数
     */
    public long getErrorCount() {
        return totalRequests - successCount;
    }

    /**
     * 获取错误细分中的异常类型总数.
     *
     * @return 所有异常类型发生次数之和
     */
    public long getTotalErrorBreakdownCount() {
        return errorBreakdown.values().stream().mapToLong(Long::longValue).sum();
    }
}
