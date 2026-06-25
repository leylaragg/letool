package com.github.leyland.letool.log.trace;

import com.github.leyland.letool.log.config.LogProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Web 请求 TraceId 注入过滤器 —— 从请求头提取或生成 TraceId，写入 MDC 和响应头.
 *
 * <h3>执行流程</h3>
 * <pre>
 *   1. 从请求 Header（默认 X-Trace-Id）读取 TraceId —— 支持网关/上游传递
 *   2. 如果 Header 为空且 generateIfAbsent=true → 自动生成 UUID 短格式
 *   3. 写入 MDC，后续所有日志自动携带 TraceId
 *   4. 执行业务链
 *   5. finally 块：写入响应 Header → 清理 MDC（防止线程池复用时内存泄漏）
 * </pre>
 */
public class TraceIdFilter implements Filter {

    private final LogProperties properties;

    public TraceIdFilter(LogProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // ==== 1. 尝试从请求 Header 获取已有 TraceId（网关/上游服务传递）====
        String traceId = httpReq.getHeader(properties.getTrace().getHeaderName());

        // ==== 2. 无 TraceId 且配置允许生成 → 自动生成短 UUID ====
        if (traceId == null || traceId.isEmpty()) {
            if (properties.getTrace().isGenerateIfAbsent()) {
                traceId = TraceIdGenerator.uuidShort();
            }
        }

        // ==== 3. 写入 MDC，后续所有日志输出自动包含 TraceId ====
        if (traceId != null) {
            TraceContext.setTraceId(traceId);
            // 同时写入响应 Header，方便前端/调用方从响应中获取
            httpResp.setHeader(properties.getTrace().getHeaderName(), traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // ==== 4. 清理 MDC —— 防止 Tomcat 线程池复用时，下一请求沿用旧的 TraceId ====
            TraceContext.clear();
        }
    }
}
