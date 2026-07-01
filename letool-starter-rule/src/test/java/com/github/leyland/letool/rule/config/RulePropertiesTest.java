package com.github.leyland.letool.rule.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleProperties 测试")
class RulePropertiesTest {

    private RuleProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RuleProperties();
    }

    @Nested
    @DisplayName("默认值")
    class DefaultValuesTests {

        @Test
        @DisplayName("enabled 默认为 true")
        void enabledShouldDefaultTrue() {
            assertTrue(properties.isEnabled());
        }

        @Test
        @DisplayName("source 默认为 file")
        void sourceShouldDefaultFile() {
            assertEquals("file", properties.getSource());
        }

        @Test
        @DisplayName("file.path 默认为 classpath:rule/chains/*.yml")
        void filePathShouldHaveDefault() {
            assertEquals("classpath:rule/chains/*.yml", properties.getFile().getPath());
        }

        @Test
        @DisplayName("file.watch 默认为 true")
        void fileWatchShouldDefaultTrue() {
            assertTrue(properties.getFile().isWatch());
        }

        @Test
        @DisplayName("groovy.cacheScripts 默认为 true")
        void groovyCacheScriptsShouldDefaultTrue() {
            assertTrue(properties.getGroovy().isCacheScripts());
        }

        @Test
        @DisplayName("groovy.compileTimeout 默认为 5")
        void groovyCompileTimeoutShouldDefaultFive() {
            assertEquals(5, properties.getGroovy().getCompileTimeout());
        }

        @Test
        @DisplayName("hotReload.enabled 默认为 true")
        void hotReloadEnabledShouldDefaultTrue() {
            assertTrue(properties.getHotReload().isEnabled());
        }

        @Test
        @DisplayName("hotReload.checkInterval 默认为 10")
        void hotReloadCheckIntervalShouldDefaultTen() {
            assertEquals(10, properties.getHotReload().getCheckInterval());
        }

        @Test
        @DisplayName("monitoring.enabled 默认为 true")
        void monitoringEnabledShouldDefaultTrue() {
            assertTrue(properties.getMonitoring().isEnabled());
        }
    }

    @Nested
    @DisplayName("setter 操作")
    class SetterTests {

        @Test
        @DisplayName("setEnabled 应正确设置")
        void shouldSetEnabled() {
            properties.setEnabled(false);
            assertFalse(properties.isEnabled());
        }

        @Test
        @DisplayName("setSource 应正确设置")
        void shouldSetSource() {
            properties.setSource("database");
            assertEquals("database", properties.getSource());
        }

        @Test
        @DisplayName("FileConfig 的 setter 应正确设置")
        void shouldSetFileConfig() {
            RuleProperties.FileConfig fileConfig = new RuleProperties.FileConfig();
            fileConfig.setPath("/etc/rule/chains/");
            fileConfig.setWatch(false);

            assertEquals("/etc/rule/chains/", fileConfig.getPath());
            assertFalse(fileConfig.isWatch());
        }

        @Test
        @DisplayName("GroovyConfig 的 setter 应正确设置")
        void shouldSetGroovyConfig() {
            RuleProperties.GroovyConfig groovyConfig = new RuleProperties.GroovyConfig();
            groovyConfig.setScriptPath("/scripts/");
            groovyConfig.setCacheScripts(false);
            groovyConfig.setCompileTimeout(10);

            assertEquals("/scripts/", groovyConfig.getScriptPath());
            assertFalse(groovyConfig.isCacheScripts());
            assertEquals(10, groovyConfig.getCompileTimeout());
        }

        @Test
        @DisplayName("HotReloadConfig 的 setter 应正确设置")
        void shouldSetHotReloadConfig() {
            RuleProperties.HotReloadConfig config = new RuleProperties.HotReloadConfig();
            config.setEnabled(false);
            config.setCheckInterval(30);

            assertFalse(config.isEnabled());
            assertEquals(30, config.getCheckInterval());
        }

        @Test
        @DisplayName("MonitoringConfig 的 setter 应正确设置")
        void shouldSetMonitoringConfig() {
            RuleProperties.MonitoringConfig config = new RuleProperties.MonitoringConfig();
            config.setEnabled(false);

            assertFalse(config.isEnabled());
        }
    }

    @Nested
    @DisplayName("嵌套配置替换")
    class NestedConfigTests {

        @Test
        @DisplayName("setFile 应替换整个 FileConfig")
        void shouldReplaceFileConfig() {
            RuleProperties.FileConfig newConfig = new RuleProperties.FileConfig();
            newConfig.setPath("/custom/path/");
            properties.setFile(newConfig);

            assertEquals("/custom/path/", properties.getFile().getPath());
        }

        @Test
        @DisplayName("setGroovy 应替换整个 GroovyConfig")
        void shouldReplaceGroovyConfig() {
            RuleProperties.GroovyConfig newConfig = new RuleProperties.GroovyConfig();
            newConfig.setCompileTimeout(30);
            properties.setGroovy(newConfig);

            assertEquals(30, properties.getGroovy().getCompileTimeout());
        }
    }
}
