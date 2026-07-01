package com.github.leyland.letool.ratelimiter.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitResult 限流结果模型测试")
class RateLimitResultTest {

    @Nested
    @DisplayName("allow() 工厂方法测试")
    class AllowFactoryTests {

        @Test
        @DisplayName("应创建允许通过的结果")
        void shouldCreateAllowResult() {
            RateLimitResult result = RateLimitResult.allow(50);

            assertTrue(result.isAllowed());
            assertEquals(50, result.getAvailablePermits());
            assertEquals(0, result.getWaitTimeMs());
        }

        @Test
        @DisplayName("allow 结果的 waitTimeMs 应为 0")
        void allowResultShouldHaveZeroWaitTime() {
            RateLimitResult result = RateLimitResult.allow(0);
            assertEquals(0, result.getWaitTimeMs());
        }

        @Test
        @DisplayName("allow 结果 availablePermits 可为 0")
        void allowResultCanHaveZeroPermits() {
            RateLimitResult result = RateLimitResult.allow(0);

            assertTrue(result.isAllowed());
            assertEquals(0, result.getAvailablePermits());
        }
    }

    @Nested
    @DisplayName("deny() 工厂方法测试")
    class DenyFactoryTests {

        @Test
        @DisplayName("应创建被拒绝的结果")
        void shouldCreateDenyResult() {
            RateLimitResult result = RateLimitResult.deny(2000);

            assertFalse(result.isAllowed());
            assertEquals(2000, result.getWaitTimeMs());
            assertEquals(0, result.getAvailablePermits());
        }

        @Test
        @DisplayName("deny 结果的 availablePermits 应为 0")
        void denyResultShouldHaveZeroPermits() {
            RateLimitResult result = RateLimitResult.deny(500);
            assertEquals(0, result.getAvailablePermits());
        }

        @Test
        @DisplayName("deny 结果的 waitTimeMs 可为 0")
        void denyResultCanHaveZeroWaitTime() {
            RateLimitResult result = RateLimitResult.deny(0);

            assertFalse(result.isAllowed());
            assertEquals(0, result.getWaitTimeMs());
        }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

        @Test
        @DisplayName("allow 结果的 toString 应包含 allowed=true")
        void allowToStringShouldContainAllowedTrue() {
            RateLimitResult result = RateLimitResult.allow(10);
            assertTrue(result.toString().contains("allowed=true"));
            assertTrue(result.toString().contains("availablePermits=10"));
        }

        @Test
        @DisplayName("deny 结果的 toString 应包含 allowed=false")
        void denyToStringShouldContainAllowedFalse() {
            RateLimitResult result = RateLimitResult.deny(1000);
            assertTrue(result.toString().contains("allowed=false"));
            assertTrue(result.toString().contains("waitTimeMs=1000"));
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("allow 工厂方法返回不同实例")
        void allowReturnsDifferentInstances() {
            RateLimitResult r1 = RateLimitResult.allow(10);
            RateLimitResult r2 = RateLimitResult.allow(10);
            assertNotSame(r1, r2);
        }

        @Test
        @DisplayName("deny 工厂方法返回不同实例")
        void denyReturnsDifferentInstances() {
            RateLimitResult r1 = RateLimitResult.deny(1000);
            RateLimitResult r2 = RateLimitResult.deny(1000);
            assertNotSame(r1, r2);
        }
    }
}
