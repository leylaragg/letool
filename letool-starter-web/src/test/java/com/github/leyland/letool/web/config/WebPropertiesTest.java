package com.github.leyland.letool.web.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebProperties 配置属性测试")
class WebPropertiesTest {

    private WebProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebProperties();
    }

    @Test
    @DisplayName("默认 enabled 为 true")
    void testDefaultEnabled() {
        assertTrue(properties.isEnabled());
    }

    @Test
    @DisplayName("setEnabled")
    void testSetEnabled() {
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }

    @Test
    @DisplayName("ResponseWrapper 默认值")
    void testResponseWrapperDefaults() {
        WebProperties.ResponseWrapper rw = properties.getResponseWrapper();
        assertTrue(rw.isEnabled());
        assertTrue(rw.getExcludePaths().isEmpty());
    }

    @Test
    @DisplayName("ResponseWrapper setter")
    void testResponseWrapperSetters() {
        WebProperties.ResponseWrapper rw = new WebProperties.ResponseWrapper();
        rw.setEnabled(false);
        rw.setExcludePaths(List.of("/health", "/actuator/**"));
        assertFalse(rw.isEnabled());
        assertEquals(2, rw.getExcludePaths().size());
    }

    @Test
    @DisplayName("XssFilter 默认值")
    void testXssFilterDefaults() {
        WebProperties.XssFilter xssFilter = properties.getXssFilter();
        assertTrue(xssFilter.isEnabled());
        assertTrue(xssFilter.getExcludePaths().isEmpty());
    }

    @Test
    @DisplayName("XssFilter setter")
    void testXssFilterSetters() {
        WebProperties.XssFilter xssFilter = new WebProperties.XssFilter();
        xssFilter.setEnabled(false);
        xssFilter.setExcludePaths(List.of("/api/file/**"));
        assertFalse(xssFilter.isEnabled());
        assertEquals(1, xssFilter.getExcludePaths().size());
    }

    @Test
    @DisplayName("SqlInjectionFilter 默认值")
    void testSqlInjectionFilterDefaults() {
        WebProperties.SqlInjectionFilter filter = properties.getSqlInjectionFilter();
        assertTrue(filter.isEnabled());
    }

    @Test
    @DisplayName("SqlInjectionFilter setter")
    void testSqlInjectionFilterSetter() {
        WebProperties.SqlInjectionFilter filter = new WebProperties.SqlInjectionFilter();
        filter.setEnabled(false);
        assertFalse(filter.isEnabled());
    }

    @Test
    @DisplayName("RequestLog 默认值")
    void testRequestLogDefaults() {
        WebProperties.RequestLog requestLog = properties.getRequestLog();
        assertTrue(requestLog.isEnabled());
        assertFalse(requestLog.isIncludeBody());
        assertEquals(4096, requestLog.getMaxBodySize());
    }

    @Test
    @DisplayName("RequestLog setter")
    void testRequestLogSetters() {
        WebProperties.RequestLog requestLog = new WebProperties.RequestLog();
        requestLog.setEnabled(false);
        requestLog.setIncludeBody(true);
        requestLog.setMaxBodySize(8192);
        assertFalse(requestLog.isEnabled());
        assertTrue(requestLog.isIncludeBody());
        assertEquals(8192, requestLog.getMaxBodySize());
    }

    @Test
    @DisplayName("所有子配置非空")
    void testAllSubConfigsNotNull() {
        assertNotNull(properties.getResponseWrapper());
        assertNotNull(properties.getXssFilter());
        assertNotNull(properties.getSqlInjectionFilter());
        assertNotNull(properties.getRequestLog());
    }
}
