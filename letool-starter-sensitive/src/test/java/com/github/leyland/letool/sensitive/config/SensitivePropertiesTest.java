package com.github.leyland.letool.sensitive.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveProperties 测试")
class SensitivePropertiesTest {

    private SensitiveProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SensitiveProperties();
    }

    @Test
    @DisplayName("默认应为启用状态")
    void shouldBeEnabledByDefault() {
        assertTrue(properties.isEnabled());
    }

    @Test
    @DisplayName("setEnabled 应正确设置")
    void shouldSetEnabled() {
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }

    @Test
    @DisplayName("Jackson 应默认启用")
    void shouldEnableJacksonByDefault() {
        assertTrue(properties.getJackson().isEnabled());
    }

    @Test
    @DisplayName("Log 应默认启用")
    void shouldEnableLogByDefault() {
        assertTrue(properties.getLog().isEnabled());
    }

    @Test
    @DisplayName("Jackson 配置 setter 应正确设置")
    void shouldSetJacksonConfig() {
        SensitiveProperties.Jackson config = new SensitiveProperties.Jackson();
        config.setEnabled(false);

        assertFalse(config.isEnabled());
    }

    @Test
    @DisplayName("Log 配置 setter 应正确设置")
    void shouldSetLogConfig() {
        SensitiveProperties.Log config = new SensitiveProperties.Log();
        config.setEnabled(false);

        assertFalse(config.isEnabled());
    }
}
