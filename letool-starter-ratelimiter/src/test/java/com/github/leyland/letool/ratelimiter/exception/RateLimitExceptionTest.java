package com.github.leyland.letool.ratelimiter.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitException 限流异常测试")
class RateLimitExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造函数应正确设置消息")
        void messageConstructorShouldSetMessage() {
            RateLimitException ex = new RateLimitException("请求被限流");

            assertEquals("请求被限流", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause) 构造函数应设置消息和原因")
        void messageCauseConstructorShouldSetBoth() {
            Throwable cause = new RuntimeException("原始错误");
            RateLimitException ex = new RateLimitException("限流失败", cause);

            assertEquals("限流失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            RateLimitException ex = new RateLimitException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("独立实例测试")
    class IndependenceTests {

        @Test
        @DisplayName("不同异常实例消息应独立")
        void differentInstancesShouldHaveIndependentMessages() {
            RateLimitException ex1 = new RateLimitException("msg1");
            RateLimitException ex2 = new RateLimitException("msg2");

            assertEquals("msg1", ex1.getMessage());
            assertEquals("msg2", ex2.getMessage());
        }
    }
}
