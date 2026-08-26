package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 缓存严格写失败分类的稳定异常契约测试。 */
@DisplayName("CacheWriteFailureSupport 测试")
class CacheWriteFailureSupportTest {

    @Test
    @DisplayName("严格策略应以 CACHE_006 原样保留 Redis 根因")
    void strictPolicyShouldPropagateCache006WithOriginalCause() {
        RuntimeException cause = new RuntimeException("redis down");

        CacheException thrown = assertThrows(CacheException.class,
                () -> CacheWriteFailureSupport.throwIfStrict(
                        CacheWriteFailurePolicy.FAIL_CLOSED, cause));

        assertEquals("CACHE_006", thrown.getCode());
        assertSame(cause, thrown.getCause());
    }

    @Test
    @DisplayName("兼容策略不应改变原有控制流")
    void bestEffortShouldNotThrow() {
        assertDoesNotThrow(() -> CacheWriteFailureSupport.throwIfStrict(
                CacheWriteFailurePolicy.BEST_EFFORT,
                new RuntimeException("redis down")));
    }

    @Test
    @DisplayName("无法确认结果时应生成包含 Redis 操作名称的根因")
    void shouldDescribeUnconfirmedRedisOperation() {
        IllegalStateException cause = CacheWriteFailureSupport.unconfirmed("SADD");

        assertEquals("Redis SADD 未返回可确认结果", cause.getMessage());
    }
}
