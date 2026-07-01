package com.github.leyland.letool.cache.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheProperties 配置属性测试")
class CachePropertiesTest {

    private CacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CacheProperties();
    }

    @Test
    @DisplayName("默认值 - enabled 为 true")
    void testDefaultEnabled() {
        assertTrue(properties.isEnabled());
    }

    @Test
    @DisplayName("默认值 - redisPrefix")
    void testDefaultRedisPrefix() {
        assertEquals("letool:cache:", properties.getRedisPrefix());
    }

    @Test
    @DisplayName("默认值 - instances 为空列表")
    void testDefaultInstances() {
        assertNotNull(properties.getInstances());
        assertTrue(properties.getInstances().isEmpty());
    }

    @Test
    @DisplayName("默认值 - degradation")
    void testDefaultDegradation() {
        assertNotNull(properties.getDegradation());
        assertEquals(Duration.ofSeconds(30), properties.getDegradation().getRecoveryInterval());
        assertEquals(3, properties.getDegradation().getMaxRetryCount());
    }

    @Test
    @DisplayName("默认值 - monitoring")
    void testDefaultMonitoring() {
        assertNotNull(properties.getMonitoring());
        assertTrue(properties.getMonitoring().isEnabled());
    }

    @Test
    @DisplayName("setEnabled 修改总开关")
    void testSetEnabled() {
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }

    @Test
    @DisplayName("setRedisPrefix 修改前缀")
    void testSetRedisPrefix() {
        properties.setRedisPrefix("custom:");
        assertEquals("custom:", properties.getRedisPrefix());
    }

    @Test
    @DisplayName("setInstances 设置实例列表")
    void testSetInstances() {
        CacheProperties.InstanceConfig config = new CacheProperties.InstanceConfig();
        config.setName("testCache");
        properties.setInstances(List.of(config));
        assertEquals(1, properties.getInstances().size());
        assertEquals("testCache", properties.getInstances().get(0).getName());
    }

    @Test
    @DisplayName("Degradation - setRecoveryInterval / setMaxRetryCount")
    void testDegradationSetters() {
        CacheProperties.Degradation d = new CacheProperties.Degradation();
        d.setRecoveryInterval(Duration.ofSeconds(60));
        d.setMaxRetryCount(5);
        assertEquals(Duration.ofSeconds(60), d.getRecoveryInterval());
        assertEquals(5, d.getMaxRetryCount());
    }

    @Test
    @DisplayName("Monitoring - setEnabled")
    void testMonitoringSetter() {
        CacheProperties.Monitoring m = new CacheProperties.Monitoring();
        m.setEnabled(false);
        assertFalse(m.isEnabled());
    }

    @Test
    @DisplayName("InstanceConfig - 所有默认值")
    void testInstanceConfigDefaults() {
        CacheProperties.InstanceConfig config = new CacheProperties.InstanceConfig();
        assertEquals(2000, config.getL1MaxSize());
        assertEquals(Duration.ofHours(24), config.getL1Ttl());
        assertEquals(Duration.ofDays(3), config.getL2Ttl());
        assertTrue(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(5), config.getNullValueTtl());
        assertNull(config.getName());
    }

    @Test
    @DisplayName("InstanceConfig - 所有 setter")
    void testInstanceConfigSetters() {
        CacheProperties.InstanceConfig config = new CacheProperties.InstanceConfig();
        config.setName("myCache");
        config.setL1MaxSize(500);
        config.setL1Ttl(Duration.ofHours(1));
        config.setL2Ttl(Duration.ofDays(7));
        config.setNullValueCache(false);
        config.setNullValueTtl(Duration.ofMinutes(10));

        assertEquals("myCache", config.getName());
        assertEquals(500, config.getL1MaxSize());
        assertEquals(Duration.ofHours(1), config.getL1Ttl());
        assertEquals(Duration.ofDays(7), config.getL2Ttl());
        assertFalse(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(10), config.getNullValueTtl());
    }
}
