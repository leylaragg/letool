package com.github.leyland.letool.ratelimiter.circuit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultCircuitBreaker 熔断器测试")
class DefaultCircuitBreakerTest {

    private DefaultCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new DefaultCircuitBreaker("test-service", 0.5, 60, 5, 3);
    }

    @Nested
    @DisplayName("构造和初始状态测试")
    class ConstructionTests {

        @Test
        @DisplayName("初始状态应为 CLOSED")
        void initialStateShouldBeClosed() {
            assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        }

        @Test
        @DisplayName("getName 应返回构造时传入的名称")
        void getNameShouldReturnConstructedName() {
            assertEquals("test-service", breaker.getName());
        }

        @Test
        @DisplayName("初始失败率应为 0")
        void initialFailureRateShouldBeZero() {
            assertEquals(0.0, breaker.getFailureRate());
        }

        @Test
        @DisplayName("CLOSED 状态下 isAllowed 应返回 true")
        void closedStateShouldAllow() {
            assertTrue(breaker.isAllowed());
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN 状态转换测试")
    class ClosedToOpenTests {

        @Test
        @DisplayName("失败率超过阈值应触发熔断")
        void shouldTripWhenFailureRateExceedsThreshold() {
            breaker.recordSuccess();
            breaker.recordFailure();
            breaker.recordFailure();

            assertTrue(breaker.getFailureRate() >= 0.5);
            assertEquals(CircuitBreakerState.OPEN, breaker.getState());
        }

        @Test
        @DisplayName("失败率未达阈值不应触发熔断")
        void shouldNotTripWhenUnderThreshold() {
            for (int i = 0; i < 5; i++) breaker.recordSuccess();
            breaker.recordFailure();

            assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        }

        @Test
        @DisplayName("OPEN 状态下 isAllowed 应返回 false")
        void openStateShouldDeny() {
            for (int i = 0; i < 3; i++) breaker.recordFailure();
            assertFalse(breaker.isAllowed());
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN 状态转换测试")
    class OpenToHalfOpenTests {

        @Test
        @DisplayName("超过恢复超时后应进入 HALF_OPEN")
        void shouldTransitionToHalfOpenAfterRecoveryTimeout() {
            DefaultCircuitBreaker fastBreaker =
                    new DefaultCircuitBreaker("fast", 0.5, 60, 0, 1);

            fastBreaker.recordFailure();
            fastBreaker.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, fastBreaker.getState());

            assertTrue(fastBreaker.isAllowed());
            assertEquals(CircuitBreakerState.HALF_OPEN, fastBreaker.getState());
        }

        @Test
        @DisplayName("未超过恢复超时应保持 OPEN")
        void shouldStayOpenBeforeRecoveryTimeout() {
            DefaultCircuitBreaker slowBreaker =
                    new DefaultCircuitBreaker("slow", 0.5, 60, 999, 1);

            for (int i = 0; i < 3; i++) slowBreaker.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, slowBreaker.getState());

            assertFalse(slowBreaker.isAllowed());
            assertEquals(CircuitBreakerState.OPEN, slowBreaker.getState());
        }
    }

    @Nested
    @DisplayName("HALF_OPEN → CLOSED 恢复测试")
    class HalfOpenToClosedTests {

        @Test
        @DisplayName("半开状态下试探全部成功应恢复")
        void shouldRecoverWhenAllTrialsSucceed() {
            DefaultCircuitBreaker cb =
                    new DefaultCircuitBreaker("recover", 0.5, 60, 0, 2);

            cb.recordFailure();
            cb.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, cb.getState());

            cb.isAllowed();
            assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

            cb.recordSuccess();
            cb.recordSuccess();
            assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("半开状态下任何失败应立即切回 OPEN")
        void shouldReopenOnHalfOpenFailure() {
            DefaultCircuitBreaker cb =
                    new DefaultCircuitBreaker("reopen", 0.5, 60, 0, 3);

            for (int i = 0; i < 3; i++) cb.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, cb.getState());

            cb.isAllowed();
            assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

            cb.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, cb.getState());
        }

        @Test
        @DisplayName("半开状态试探请求数受限（含状态转换本身的一次额外放行）")
        void halfOpenShouldLimitTrialRequests() {
            DefaultCircuitBreaker cb =
                    new DefaultCircuitBreaker("limit", 0.5, 60, 0, 2);

            for (int i = 0; i < 3; i++) cb.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, cb.getState());

            assertTrue(cb.isAllowed());   // OPEN→HALF_OPEN CAS path, counter stays 0
            assertTrue(cb.isAllowed());   // counter 1, 1≤2
            assertTrue(cb.isAllowed());   // counter 2, 2≤2
            assertFalse(cb.isAllowed(), "计数器达到 3，超过 halfOpenMaxRequests=2，应拒绝");
        }
    }

    @Nested
    @DisplayName("reset() 测试")
    class ResetTests {

        @Test
        @DisplayName("reset 应恢复到 CLOSED 状态")
        void resetShouldRestoreToClosed() {
            for (int i = 0; i < 3; i++) breaker.recordFailure();
            assertEquals(CircuitBreakerState.OPEN, breaker.getState());

            breaker.reset();
            assertEquals(CircuitBreakerState.CLOSED, breaker.getState());
        }

        @Test
        @DisplayName("reset 应清除失败率")
        void resetShouldClearFailureRate() {
            for (int i = 0; i < 3; i++) breaker.recordFailure();
            breaker.reset();

            assertEquals(0.0, breaker.getFailureRate());
        }

        @Test
        @DisplayName("reset 后 isAllowed 应返回 true")
        void resetShouldAllowRequests() {
            for (int i = 0; i < 3; i++) breaker.recordFailure();
            breaker.reset();

            assertTrue(breaker.isAllowed());
        }
    }

    @Nested
    @DisplayName("统计数据测试")
    class StatisticsTests {

        @Test
        @DisplayName("无数据时失败率应为 0")
        void failureRateShouldBeZeroWithoutData() {
            assertEquals(0.0, breaker.getFailureRate());
        }

        @Test
        @DisplayName("全部成功时失败率应为 0")
        void failureRateShouldBeZeroWhenAllSuccess() {
            for (int i = 0; i < 5; i++) breaker.recordSuccess();
            assertEquals(0.0, breaker.getFailureRate());
        }

        @Test
        @DisplayName("全部失败时失败率应为 1.0")
        void failureRateShouldBeOneWhenAllFail() {
            for (int i = 0; i < 3; i++) breaker.recordFailure();
            assertEquals(1.0, breaker.getFailureRate());
        }

        @Test
        @DisplayName("混合结果应正确计算失败率")
        void failureRateShouldBeCalculatedCorrectly() {
            breaker.recordSuccess();
            breaker.recordSuccess();
            breaker.recordSuccess();
            breaker.recordFailure();

            assertEquals(0.25, breaker.getFailureRate());
        }
    }
}
