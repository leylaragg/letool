package com.github.leyland.letool.job.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryPolicy 重试策略测试")
class RetryPolicyTest {

    @Nested
    @DisplayName("shouldRetry 测试")
    class ShouldRetryTests {

        @Test
        @DisplayName("currentRetry < maxRetries 时应返回 true")
        void shouldRetryWhenUnderMax() {
            assertTrue(RetryPolicy.shouldRetry(0, 3));
            assertTrue(RetryPolicy.shouldRetry(1, 3));
            assertTrue(RetryPolicy.shouldRetry(2, 3));
        }

        @Test
        @DisplayName("currentRetry >= maxRetries 时应返回 false")
        void shouldNotRetryWhenAtOrOverMax() {
            assertFalse(RetryPolicy.shouldRetry(3, 3));
            assertFalse(RetryPolicy.shouldRetry(4, 3));
            assertFalse(RetryPolicy.shouldRetry(10, 3));
        }

        @Test
        @DisplayName("maxRetries=0 时任何 currentRetry 都不应重试")
        void zeroMaxRetriesShouldNeverRetry() {
            assertFalse(RetryPolicy.shouldRetry(0, 0));
            assertFalse(RetryPolicy.shouldRetry(1, 0));
        }
    }

    @Nested
    @DisplayName("getBackoffDelay 测试")
    class GetBackoffDelayTests {

        @Test
        @DisplayName("第0次重试延迟应为 baseMs")
        void firstRetryShouldBeBaseMs() {
            assertEquals(1000, RetryPolicy.getBackoffDelay(0, 1000, 2.0));
        }

        @Test
        @DisplayName("第1次重试延迟应为 baseMs * multiplier")
        void secondRetryShouldBeBaseTimesMultiplier() {
            assertEquals(2000, RetryPolicy.getBackoffDelay(1, 1000, 2.0));
        }

        @Test
        @DisplayName("第2次重试延迟应为 baseMs * multiplier^2")
        void thirdRetryShouldBeBaseTimesMultiplierSquared() {
            assertEquals(4000, RetryPolicy.getBackoffDelay(2, 1000, 2.0));
        }

        @Test
        @DisplayName("第3次重试延迟应为 baseMs * multiplier^3")
        void fourthRetryShouldBeCorrect() {
            assertEquals(8000, RetryPolicy.getBackoffDelay(3, 1000, 2.0));
        }

        @Test
        @DisplayName("退避延迟应被限制在 60 秒以内")
        void delayShouldBeCappedAt60Seconds() {
            long delay = RetryPolicy.getBackoffDelay(10, 10000, 2.0);
            assertTrue(delay <= 60000);
            assertEquals(60000, delay);
        }

        @Test
        @DisplayName("负数 retryCount 应返回 0")
        void negativeRetryCountShouldReturnZero() {
            assertEquals(0, RetryPolicy.getBackoffDelay(-1, 1000, 2.0));
        }

        @Test
        @DisplayName("baseMs <= 0 应返回 0")
        void nonPositiveBaseMsShouldReturnZero() {
            assertEquals(0, RetryPolicy.getBackoffDelay(0, 0, 2.0));
            assertEquals(0, RetryPolicy.getBackoffDelay(0, -100, 2.0));
        }

        @Test
        @DisplayName("multiplier <= 0 应返回 0")
        void nonPositiveMultiplierShouldReturnZero() {
            assertEquals(0, RetryPolicy.getBackoffDelay(0, 1000, 0));
            assertEquals(0, RetryPolicy.getBackoffDelay(0, 1000, -1.0));
        }
    }

    @Nested
    @DisplayName("工具类不可实例化测试")
    class UtilityClassTests {

        @Test
        @DisplayName("构造应抛 UnsupportedOperationException")
        void constructorShouldThrow() {
            assertThrows(UnsupportedOperationException.class, () -> {
                try {
                    java.lang.reflect.Constructor<RetryPolicy> ctor = RetryPolicy.class.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    ctor.newInstance();
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        }
    }
}
