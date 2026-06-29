package com.github.leyland.letool.monitor.metrics;

import java.time.LocalDateTime;

/**
 * HTTP 请求指标快照.
 *
 * <p>针对单个 API 路径和 HTTP 方法的聚合统计数据，包含请求量、响应状态分布、
 * 响应时间百分位数等关键 HTTP 性能指标。</p>
 *
 * <h3>状态码分类</h3>
 * <ul>
 *   <li>2xx — 成功请求</li>
 *   <li>4xx — 客户端错误</li>
 *   <li>5xx — 服务端错误</li>
 * </ul>
 *
 * <h3>百分位延迟</h3>
 * <p>提供 P50、P90、P95、P99 四个常用百分位，便于评估尾部延迟。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class HttpMetrics {

    // ======================== 字段 ========================

    /** API 路径 */
    private String path;

    /** HTTP 方法（GET / POST / PUT / DELETE 等） */
    private String method;

    /** 总请求次数 */
    private long totalRequests;

    /** 2xx 成功响应次数 */
    private long successCount;

    /** 4xx 客户端错误次数 */
    private long clientErrorCount;

    /** 5xx 服务端错误次数 */
    private long serverErrorCount;

    /** 平均响应时间（毫秒） */
    private double avgResponseTimeMs;

    /** P50 百分位响应时间（毫秒） */
    private double p50Ms;

    /** P90 百分位响应时间（毫秒） */
    private double p90Ms;

    /** P95 百分位响应时间（毫秒） */
    private double p95Ms;

    /** P99 百分位响应时间（毫秒） */
    private double p99Ms;

    /** 统计窗口起始时间 */
    private LocalDateTime windowStart;

    /** 统计窗口结束时间 */
    private LocalDateTime windowEnd;

    // ======================== 构造方法 ========================

    /**
     * 创建空的 HTTP 指标快照.
     */
    public HttpMetrics() {
    }

    /**
     * 创建 HTTP 指标快照并初始化所有字段.
     *
     * @param path              API 路径
     * @param method            HTTP 方法
     * @param totalRequests     总请求次数
     * @param successCount      2xx 成功次数
     * @param clientErrorCount  4xx 客户端错误次数
     * @param serverErrorCount  5xx 服务端错误次数
     * @param avgResponseTimeMs 平均响应时间（毫秒）
     * @param p50Ms            P50 响应时间（毫秒）
     * @param p90Ms            P90 响应时间（毫秒）
     * @param p95Ms            P95 响应时间（毫秒）
     * @param p99Ms            P99 响应时间（毫秒）
     * @param windowStart       统计窗口起始时间
     * @param windowEnd         统计窗口结束时间
     */
    public HttpMetrics(String path, String method, long totalRequests, long successCount,
                       long clientErrorCount, long serverErrorCount, double avgResponseTimeMs,
                       double p50Ms, double p90Ms, double p95Ms, double p99Ms,
                       LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.path = path;
        this.method = method;
        this.totalRequests = totalRequests;
        this.successCount = successCount;
        this.clientErrorCount = clientErrorCount;
        this.serverErrorCount = serverErrorCount;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.p50Ms = p50Ms;
        this.p90Ms = p90Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
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

    public long getClientErrorCount() { return clientErrorCount; }
    public void setClientErrorCount(long clientErrorCount) { this.clientErrorCount = clientErrorCount; }

    public long getServerErrorCount() { return serverErrorCount; }
    public void setServerErrorCount(long serverErrorCount) { this.serverErrorCount = serverErrorCount; }

    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public double getP50Ms() { return p50Ms; }
    public void setP50Ms(double p50Ms) { this.p50Ms = p50Ms; }

    public double getP90Ms() { return p90Ms; }
    public void setP90Ms(double p90Ms) { this.p90Ms = p90Ms; }

    public double getP95Ms() { return p95Ms; }
    public void setP95Ms(double p95Ms) { this.p95Ms = p95Ms; }

    public double getP99Ms() { return p99Ms; }
    public void setP99Ms(double p99Ms) { this.p99Ms = p99Ms; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    // ======================== 便捷方法 ========================

    /**
     * 计算请求成功率.
     *
     * @return 成功率（0.0 ~ 1.0），无请求时返回 0
     */
    public double getSuccessRate() {
        if (totalRequests == 0) return 0.0;
        return (double) successCount / totalRequests;
    }

    /**
     * 计算错误率（4xx + 5xx）.
     *
     * @return 错误率（0.0 ~ 1.0），无请求时返回 0
     */
    public double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return (double) (clientErrorCount + serverErrorCount) / totalRequests;
    }
}
