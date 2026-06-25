package com.github.leyland.letool.log.trace;

import org.slf4j.MDC;

/**
 * 线程上下文追踪 —— 封装 MDC 操作，提供 getTraceId / setTraceId / clear.
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {}

    /**
     * 获取当前线程的 TraceId.
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置当前线程的 TraceId.
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 获取或生成 TraceId —— 有则返回，无则生成.
     */
    public static String getOrGenerate() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdGenerator.uuidShort();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 清除当前线程的 MDC 上下文.
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
