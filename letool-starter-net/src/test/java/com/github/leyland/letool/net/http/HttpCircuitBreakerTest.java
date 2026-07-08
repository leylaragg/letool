package com.github.leyland.letool.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpCircuitBreaker 熔断器测试")
@ExtendWith(OutputCaptureExtension.class)
class HttpCircuitBreakerTest {

    @Nested
    @DisplayName("CLOSED 状态")
    class ClosedState {

        @Test
        @DisplayName("初始状态为 CLOSED")
        void initialStateClosed() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 10, 30);
            assertEquals(HttpCircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("CLOSED 状态允许请求通过")
        void allowRequestWhenClosed() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 10, 30);
            assertTrue(cb.allowRequest());
        }

        @Test
        @DisplayName("低于阈值时保持 CLOSED")
        void belowThresholdStaysClosed() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 10, 30);
            for (int i = 0; i < 10; i++) {
                cb.recordSuccess();
            }
            cb.recordFailure(); // 1/11 < 0.5
            assertEquals(HttpCircuitBreaker.State.CLOSED, cb.getState());
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN 转换")
    class ClosedToOpen {

        @Test
        @DisplayName("超过失败率阈值触发熔断")
        void exceedThresholdTripsToOpen() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 30);
            // 6 failures out of 6 → error rate = 1.0 > 0.5
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            assertEquals(HttpCircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("熔断后 allowRequest 返回 false")
        void allowRequestFalseWhenOpen() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 30);
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            assertEquals(HttpCircuitBreaker.State.OPEN, cb.getState());
            assertFalse(cb.allowRequest());
        }

        @Test
        @DisplayName("熔断日志应输出可读百分比")
        void openLogShouldRenderReadablePercentages(CapturedOutput output) {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 30);

            cb.recordFailure();

            assertTrue(output.getOut().contains("Circuit breaker OPEN"));
            assertTrue(output.getOut().contains("error rate=100.00%"));
            assertTrue(output.getOut().contains("threshold=50.00%"));
            assertFalse(output.getOut().contains("{:.2%}"));
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN 恢复探测")
    class OpenToHalfOpen {

        @Test
        @DisplayName("恢复超时后自动转为 HALF_OPEN")
        void recoveryTimeoutTransitionsToHalfOpen() throws InterruptedException {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 1);
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            assertEquals(HttpCircuitBreaker.State.OPEN, cb.getState());

            Thread.sleep(1100); // 超过 1 秒恢复时间
            assertTrue(cb.allowRequest());
            assertEquals(HttpCircuitBreaker.State.HALF_OPEN, cb.getState());
        }
    }

    @Nested
    @DisplayName("HALF_OPEN 状态转换")
    class HalfOpenTransitions {

        @Test
        @DisplayName("HALF_OPEN 试探成功 → CLOSED")
        void halfOpenSuccessBackToClosed() throws InterruptedException {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 1);
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            Thread.sleep(1100);
            cb.allowRequest(); // enters HALF_OPEN
            assertEquals(HttpCircuitBreaker.State.HALF_OPEN, cb.getState());

            cb.recordSuccess(); // success → CLOSED
            assertEquals(HttpCircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("HALF_OPEN 试探失败 → OPEN")
        void halfOpenFailureBackToOpen() throws InterruptedException {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 1);
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            Thread.sleep(1100);
            cb.allowRequest(); // enters HALF_OPEN
            assertEquals(HttpCircuitBreaker.State.HALF_OPEN, cb.getState());

            cb.recordFailure(); // failure → OPEN
            assertEquals(HttpCircuitBreaker.State.OPEN, cb.getState());
        }
    }

    @Nested
    @DisplayName("reset - 强制重置")
    class Reset {

        @Test
        @DisplayName("reset 恢复到 CLOSED")
        void resetOpenedBreaker() {
            HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 30);
            for (int i = 0; i < 6; i++) {
                cb.recordFailure();
            }
            assertEquals(HttpCircuitBreaker.State.OPEN, cb.getState());
            cb.reset();
            assertEquals(HttpCircuitBreaker.State.CLOSED, cb.getState());
        }
    }
}
