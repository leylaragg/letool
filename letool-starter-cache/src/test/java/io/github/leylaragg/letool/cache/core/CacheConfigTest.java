package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.consistency.CacheReadValidation;
import io.github.leylaragg.letool.cache.consistency.CacheWritePolicy;
import io.github.leylaragg.letool.cache.exception.CacheException;
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
        assertTrue(config.isL1Enabled());
        assertTrue(config.isL2Enabled());
        assertTrue(config.isStrongConsistency());
        assertEquals(CacheConsistencyMode.TRANSACTIONAL, config.getConsistencyMode());
        assertEquals(CacheReadValidation.VERSIONED, config.getReadValidation());
        assertEquals(CacheWritePolicy.INVALIDATE, config.getWritePolicy());
        assertTrue(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(5), config.getNullValueTtl());
        assertEquals("letool:cache:", config.getRedisKeyPrefix());
    }

    @Test
    @DisplayName("TRANSACTIONAL 模式可以独立选择读取校验和写策略")
    void transactionalModeShouldAllowIndependentReadAndWritePolicies() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("critical")
                .consistencyMode(CacheConsistencyMode.TRANSACTIONAL)
                .readValidation(CacheReadValidation.NONE)
                .writePolicy(CacheWritePolicy.UPDATE)
                .build();

        assertEquals(CacheConsistencyMode.TRANSACTIONAL, config.getConsistencyMode());
        assertEquals(CacheReadValidation.NONE, config.getReadValidation());
        assertEquals(CacheWritePolicy.UPDATE, config.getWritePolicy());
        assertFalse(config.isStrongConsistency());
    }

    @Test
    @DisplayName("DURABLE 必须启用 L2 和 VERSIONED 读取校验")
    void durableShouldRequireL2VersionValidation() {
        assertThrows(CacheException.class, () -> CacheConfig.<String, String>builder("critical")
                .consistencyMode(CacheConsistencyMode.DURABLE)
                .readValidation(CacheReadValidation.NONE)
                .build());
    }

    @Test
    @DisplayName("KV 自定义 Key 使用显式稳定序列化函数")
    void shouldUseConfiguredStableKeySerializer() {
        CacheConfig<BusinessKey, String> config = CacheConfig.<BusinessKey, String>builder("users")
                .keySerializer(key -> key.tenantId() + ":" + key.userId())
                .build();

        assertEquals("tenant-a:42", config.serializeKey(new BusinessKey("tenant-a", 42L)));
    }

    private record BusinessKey(String tenantId, Long userId) {
    }

    @Test
    @DisplayName("旧 strongConsistency 只映射读取校验")
    void legacyStrongConsistencyShouldOnlyMapReadValidation() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("legacy")
                .strongConsistency(false)
                .build();

        assertEquals(CacheConsistencyMode.TRANSACTIONAL, config.getConsistencyMode());
        assertEquals(CacheReadValidation.NONE, config.getReadValidation());
        assertFalse(config.isStrongConsistency());
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
                .l1Enabled(false)
                .l2Enabled(false)
                .strongConsistency(false)
                .nullValueCache(false)
                .nullValueTtl(Duration.ofMinutes(3))
                .redisKeyPrefix("myapp:")
                .build();

        assertEquals("fullCache", config.getName());
        assertEquals(1000, config.getL1MaxSize());
        assertEquals(Duration.ofMinutes(30), config.getL1Ttl());
        assertEquals(Duration.ofHours(6), config.getL2Ttl());
        assertFalse(config.isL1Enabled());
        assertFalse(config.isL2Enabled());
        assertFalse(config.isStrongConsistency());
        assertFalse(config.isNullValueCache());
        assertEquals(Duration.ofMinutes(3), config.getNullValueTtl());
        assertEquals("myapp:", config.getRedisKeyPrefix());
    }

    @Test
    @DisplayName("build 应使用统一配置异常校验必填项和 TTL")
    void testValidation() {
        assertConfigurationInvalid(
                () -> CacheConfig.builder(" ").build(),
                "name"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad").l1MaxSize(0).build(),
                "l1-max-size"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad").l1Ttl(Duration.ZERO).build(),
                "l1-ttl"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad").l2Ttl(Duration.ZERO).build(),
                "l2-ttl"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad")
                        .l1Ttl(Duration.ofMinutes(10))
                        .l2Ttl(Duration.ofMinutes(1))
                        .build(),
                "l2-ttl"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad")
                        .redisKeyPrefix(" ")
                        .build(),
                "redis-key-prefix"
        );
        assertConfigurationInvalid(
                () -> CacheConfig.builder("bad")
                        .nullValueTtl(Duration.ZERO)
                        .build(),
                "null-value-ttl"
        );
    }

    @Test
    @DisplayName("泛型支持 - 不同类型参数")
    void testGenericTypeSupport() {
        CacheConfig<Integer, Boolean> config = CacheConfig.<Integer, Boolean>builder("boolCache").build();
        assertEquals("boolCache", config.getName());
    }

    /**
     * 断言配置构建失败并返回预期安全字段名。
     *
     * @param action 待执行构建动作
     * @param field 预期配置字段名
     */
    private static void assertConfigurationInvalid(
            org.junit.jupiter.api.function.Executable action,
            String field) {
        CacheException exception =
                assertThrows(CacheException.class, action);
        assertEquals("CACHE_001", exception.getCode());
        assertTrue(exception.getMessage().contains(field));
    }
}
