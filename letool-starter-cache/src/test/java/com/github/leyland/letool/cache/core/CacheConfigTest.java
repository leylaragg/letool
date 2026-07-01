package com.github.leyland.letool.cache.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheConfig 构建器测试")
class CacheConfigTest {

    @Test
    @DisplayName("builder 创建并设置名称")
    void testBuilderName() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("testCache").build();
        assertEquals("testCache", config.getName());
    }

    @Test
    @DisplayName("默认值 - l1MaxSize, l1Ttl, l2Ttl")
    void testDefaults() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test").build();
        assertEquals(2000, config.getL1MaxSize());
        assertEquals(Duration.ofHours(24), config.getL1Ttl());
        assertEquals(Duration.ofDays(3), config.getL2Ttl());
        assertTrue(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(5), config.getNullValueTtl());
        assertEquals("letool:cache:", config.getRedisKeyPrefix());
    }

    @Test
    @DisplayName("链式调用 - l1MaxSize")
    void testL1MaxSize() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .l1MaxSize(500)
                .build();
        assertEquals(500, config.getL1MaxSize());
    }

    @Test
    @DisplayName("链式调用 - l1Ttl")
    void testL1Ttl() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .l1Ttl(Duration.ofHours(1))
                .build();
        assertEquals(Duration.ofHours(1), config.getL1Ttl());
    }

    @Test
    @DisplayName("链式调用 - l2Ttl")
    void testL2Ttl() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .l2Ttl(Duration.ofDays(7))
                .build();
        assertEquals(Duration.ofDays(7), config.getL2Ttl());
    }

    @Test
    @DisplayName("链式调用 - nullValueCache")
    void testNullValueCache() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .nullValueCache(false)
                .build();
        assertFalse(config.isNullValueCache());
    }

    @Test
    @DisplayName("链式调用 - nullValueTtl")
    void testNullValueTtl() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .nullValueTtl(Duration.ofMinutes(10))
                .build();
        assertEquals(Duration.ofMinutes(10), config.getNullValueTtl());
    }

    @Test
    @DisplayName("链式调用 - redisKeyPrefix")
    void testRedisKeyPrefix() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test")
                .redisKeyPrefix("custom:prefix:")
                .build();
        assertEquals("custom:prefix:", config.getRedisKeyPrefix());
    }

    @Test
    @DisplayName("链式调用 - 组合多个属性")
    void testChainedAll() {
        CacheConfig<Long, String> config = CacheConfig.<Long, String>builder("fullCache")
                .l1MaxSize(1000)
                .l1Ttl(Duration.ofMinutes(30))
                .l2Ttl(Duration.ofHours(6))
                .nullValueCache(false)
                .nullValueTtl(Duration.ofMinutes(3))
                .redisKeyPrefix("myapp:")
                .build();

        assertEquals("fullCache", config.getName());
        assertEquals(1000, config.getL1MaxSize());
        assertEquals(Duration.ofMinutes(30), config.getL1Ttl());
        assertEquals(Duration.ofHours(6), config.getL2Ttl());
        assertFalse(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(3), config.getNullValueTtl());
        assertEquals("myapp:", config.getRedisKeyPrefix());
    }

    @Test
    @DisplayName("泛型支持 - 不同类型参数")
    void testGenericTypeSupport() {
        CacheConfig<Integer, Boolean> config = CacheConfig.<Integer, Boolean>builder("boolCache").build();
        assertEquals("boolCache", config.getName());
    }
}
