package com.github.leyland.letool.ratelimiter.algorithm;

import com.github.leyland.letool.ratelimiter.core.RateLimitResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenBucketLimiter 令牌桶限流器测试")
class TokenBucketLimiterTest {

    private TokenBucketLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketLimiter(10, 1.0);
    }

    @AfterEach
    void tearDown() {
        limiter.shutdown();
    }

    @Nested
    @DisplayName("基本限流逻辑测试")
    class BasicRateLimitTests {

        @Test
        @DisplayName("初始时应允许请求通过")
        void shouldAllowInitialRequest() {
            RateLimitResult result = limiter.tryAcquire("test", 1);
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("消耗所有令牌后应拒绝")
        void shouldDenyWhenOutOfTokens() {
            RateLimitResult result = limiter.tryAcquire("test", 10);
            assertTrue(result.isAllowed());

            result = limiter.tryAcquire("test", 1);
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("超出容量应直接拒绝")
        void shouldDenyWhenExceedingCapacity() {
            RateLimitResult result = limiter.tryAcquire("test", 20);
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("不同 key 的桶应互不影响")
        void differentKeysShouldBeIndependent() {
            limiter.tryAcquire("key1", 5);
            RateLimitResult result = limiter.tryAcquire("key2", 5);

            assertTrue(result.isAllowed());
            assertEquals(5, result.getAvailablePermits());
        }
    }

    @Nested
    @DisplayName("拒绝结果测试")
    class DenyResultTests {

        @Test
        @DisplayName("被拒绝时 waitTimeMs 应大于 0")
        void denyShouldHavePositiveWaitTime() {
            limiter.tryAcquire("test", 10);
            RateLimitResult result = limiter.tryAcquire("test", 1);

            assertFalse(result.isAllowed());
            assertTrue(result.getWaitTimeMs() > 0);
        }

        @Test
        @DisplayName("被拒绝时 availablePermits 应为 0")
        void denyShouldHaveZeroAvailablePermits() {
            limiter.tryAcquire("test", 10);
            RateLimitResult result = limiter.tryAcquire("test", 1);

            assertEquals(0, result.getAvailablePermits());
        }
    }

    @Nested
    @DisplayName("令牌补充测试")
    class RefillTests {

        @Test
        @DisplayName("随时间流逝应自动补充令牌")
        void shouldRefillTokensOverTime() throws InterruptedException {
            limiter.tryAcquire("test", 10);
            assertFalse(limiter.tryAcquire("test", 1).isAllowed());

            Thread.sleep(2000);

            RateLimitResult result = limiter.tryAcquire("test", 1);
            assertTrue(result.isAllowed(), "2 秒后应补充约 2 个令牌");
        }
    }

    @Nested
    @DisplayName("reset() 测试")
    class ResetTests {

        @Test
        @DisplayName("reset 后应恢复满令牌")
        void resetShouldRestoreFullTokens() {
            limiter.tryAcquire("test", 10);
            limiter.reset("test");
            RateLimitResult result = limiter.tryAcquire("test", 10);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("reset 不存在的 key 不应报错")
        void resetNonExistentKeyShouldNotThrow() {
            assertDoesNotThrow(() -> limiter.reset("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getAvailablePermits() 测试")
    class GetAvailablePermitsTests {

        @Test
        @DisplayName("新 key 应返回默认容量")
        void newKeyShouldReturnDefaultCapacity() {
            assertEquals(10, limiter.getAvailablePermits("newKey"));
        }

        @Test
        @DisplayName("消耗部分令牌后应返回剩余数")
        void shouldReturnRemainingTokens() {
            limiter.tryAcquire("test", 3);
            assertEquals(7, limiter.getAvailablePermits("test"));
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("同一 key 多线程并发应保持一致性")
        void concurrentAccessSameKeyShouldBeConsistent() throws Exception {
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) {
                threads[i] = new Thread(() -> limiter.tryAcquire("concurrent", 1));
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }

            long available = limiter.getAvailablePermits("concurrent");
            assertTrue(available >= 5, "并发消耗 5 个令牌后应有 5 个以上令牌");
            assertTrue(available <= 10, "令牌数不应超过容量");
        }
    }
}
