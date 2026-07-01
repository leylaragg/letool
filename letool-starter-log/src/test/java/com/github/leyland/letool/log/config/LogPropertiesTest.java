package com.github.leyland.letool.log.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogProperties 配置属性测试")
class LogPropertiesTest {

    private LogProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LogProperties();
    }

    @Test
    @DisplayName("默认值 - Trace")
    void testTraceDefaults() {
        LogProperties.Trace trace = properties.getTrace();
        assertTrue(trace.isEnabled());
        assertEquals("X-Trace-Id", trace.getHeaderName());
        assertTrue(trace.isGenerateIfAbsent());
    }

    @Test
    @DisplayName("Trace setter - enabled")
    void testTraceSetEnabled() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setEnabled(false);
        assertFalse(trace.isEnabled());
    }

    @Test
    @DisplayName("Trace setter - headerName")
    void testTraceSetHeaderName() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setHeaderName("X-Correlation-Id");
        assertEquals("X-Correlation-Id", trace.getHeaderName());
    }

    @Test
    @DisplayName("Trace setter - generateIfAbsent")
    void testTraceSetGenerateIfAbsent() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setGenerateIfAbsent(false);
        assertFalse(trace.isGenerateIfAbsent());
    }

    @Test
    @DisplayName("默认值 - Audit")
    void testAuditDefaults() {
        LogProperties.Audit audit = properties.getAudit();
        assertTrue(audit.isEnabled());
        assertTrue(audit.isAsync());
        assertEquals("file", audit.getStorage());
    }

    @Test
    @DisplayName("Audit setter - storage")
    void testAuditSetStorage() {
        LogProperties.Audit audit = new LogProperties.Audit();
        audit.setStorage("memory");
        assertEquals("memory", audit.getStorage());
    }

    @Test
    @DisplayName("Audit setter - async")
    void testAuditSetAsync() {
        LogProperties.Audit audit = new LogProperties.Audit();
        audit.setAsync(false);
        assertFalse(audit.isAsync());
    }

    @Test
    @DisplayName("默认值 - WebLog")
    void testWebLogDefaults() {
        LogProperties.WebLog webLog = properties.getWebLog();
        assertTrue(webLog.isEnabled());
        assertFalse(webLog.isIncludeHeaders());
        assertFalse(webLog.isIncludeBody());
        assertEquals(1024, webLog.getMaxBodyLength());
        assertTrue(webLog.getExcludePaths().isEmpty());
    }

    @Test
    @DisplayName("WebLog setter - includeHeaders / includeBody")
    void testWebLogSetters() {
        LogProperties.WebLog webLog = new LogProperties.WebLog();
        webLog.setIncludeHeaders(true);
        webLog.setIncludeBody(true);
        webLog.setMaxBodyLength(2048);
        webLog.setExcludePaths(List.of("/actuator/**"));
        assertTrue(webLog.isIncludeHeaders());
        assertTrue(webLog.isIncludeBody());
        assertEquals(2048, webLog.getMaxBodyLength());
        assertEquals(1, webLog.getExcludePaths().size());
        assertEquals("/actuator/**", webLog.getExcludePaths().get(0));
    }
}
