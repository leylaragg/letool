package com.github.leyland.letool.log.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TraceContext 追踪上下文测试")
class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("setTraceId 设置 traceId 到 MDC")
    void testSetTraceId() {
        TraceContext.setTraceId("trace-123");
        assertEquals("trace-123", MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("getTraceId 从 MDC 获取 traceId")
    void testGetTraceId() {
        MDC.put(TraceContext.TRACE_ID_KEY, "trace-456");
        assertEquals("trace-456", TraceContext.getTraceId());
    }

    @Test
    @DisplayName("getTraceId - MDC 中没有值时返回 null")
    void testGetTraceIdNull() {
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("setTraceId - null 值不设置")
    void testSetTraceIdNull() {
        TraceContext.setTraceId(null);
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("setTraceId - 空字符串不设置")
    void testSetTraceIdEmpty() {
        TraceContext.setTraceId("");
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("getOrGenerate - 已有 traceId 则返回已有值")
    void testGetOrGenerateExisting() {
        MDC.put(TraceContext.TRACE_ID_KEY, "existing-id");
        String result = TraceContext.getOrGenerate();
        assertEquals("existing-id", result);
    }

    @Test
    @DisplayName("getOrGenerate - 无 traceId 时自动生成")
    void testGetOrGenerateNew() {
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
        String result = TraceContext.getOrGenerate();
        assertNotNull(result);
        assertEquals(16, result.length()); // uuidShort
        assertEquals(result, MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("clear 清除 MDC 中的 traceId")
    void testClear() {
        MDC.put(TraceContext.TRACE_ID_KEY, "trace-to-clear");
        TraceContext.clear();
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
    }
}
