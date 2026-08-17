package io.github.leylaragg.letool.log.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LogProperties} 日志配置属性测试。
 */
@DisplayName("LogProperties 配置属性测试")
class LogPropertiesTest {

    private LogProperties properties;

    /**
     * 每个测试开始前创建独立的日志配置对象。
     */
    @BeforeEach
    void setUp() {
        properties = new LogProperties();
    }

    /**
     * 链路追踪配置应提供安全可用的默认值。
     */
    @Test
    @DisplayName("默认值 - Trace")
    void testTraceDefaults() {
        LogProperties.Trace trace = properties.getTrace();
        assertTrue(trace.isEnabled());
        assertEquals("X-Trace-Id", trace.getHeaderName());
        assertTrue(trace.isGenerateIfAbsent());
    }

    /**
     * 链路追踪开关应支持修改。
     */
    @Test
    @DisplayName("Trace setter - enabled")
    void testTraceSetEnabled() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setEnabled(false);
        assertFalse(trace.isEnabled());
    }

    /**
     * TraceId 请求头名称应支持修改。
     */
    @Test
    @DisplayName("Trace setter - headerName")
    void testTraceSetHeaderName() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setHeaderName("X-Correlation-Id");
        assertEquals("X-Correlation-Id", trace.getHeaderName());
    }

    /**
     * 缺失 TraceId 时的生成策略应支持修改。
     */
    @Test
    @DisplayName("Trace setter - generateIfAbsent")
    void testTraceSetGenerateIfAbsent() {
        LogProperties.Trace trace = new LogProperties.Trace();
        trace.setGenerateIfAbsent(false);
        assertFalse(trace.isGenerateIfAbsent());
    }

    /**
     * 审计日志默认应启用。
     */
    @Test
    @DisplayName("默认值 - Audit")
    void testAuditDefaults() {
        LogProperties.Audit audit = properties.getAudit();
        assertTrue(audit.isEnabled());
    }

    /**
     * 审计日志开关应支持修改。
     */
    @Test
    @DisplayName("Audit setter - enabled")
    void testAuditSetEnabled() {
        LogProperties.Audit audit = new LogProperties.Audit();
        audit.setEnabled(false);
        assertFalse(audit.isEnabled());
    }

    /**
     * Web 请求日志配置应提供保守的默认值。
     */
    @Test
    @DisplayName("默认值 - WebLog")
    void testWebLogDefaults() {
        LogProperties.WebLog webLog = properties.getWebLog();
        assertTrue(webLog.isEnabled());
        assertTrue(webLog.getExcludePaths().isEmpty());
    }

    /**
     * Web 请求日志的排除路径应支持标准绑定。
     */
    @Test
    @DisplayName("WebLog setter - excludePaths")
    void testWebLogExcludePathsSetter() {
        LogProperties.WebLog webLog = new LogProperties.WebLog();
        webLog.setExcludePaths(List.of("/actuator/**"));
        assertEquals(1, webLog.getExcludePaths().size());
        assertEquals("/actuator/**", webLog.getExcludePaths().get(0));
    }
}
