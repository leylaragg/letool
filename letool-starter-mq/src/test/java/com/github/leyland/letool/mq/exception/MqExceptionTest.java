package com.github.leyland.letool.mq.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MqException MQ 异常测试")
class MqExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造函数应正确设置消息")
        void messageConstructorShouldSetMessage() {
            MqException ex = new MqException("消息发送失败: topic=order");

            assertEquals("消息发送失败: topic=order", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause) 构造函数应设置消息和原因")
        void messageCauseConstructorShouldSetBoth() {
            Throwable cause = new RuntimeException("连接超时");
            MqException ex = new MqException("连接失败", cause);

            assertEquals("连接失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            MqException ex = new MqException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("独立实例测试")
    class IndependenceTests {

        @Test
        @DisplayName("不同实例消息应独立")
        void differentInstancesShouldHaveIndependentMessages() {
            MqException ex1 = new MqException("msg1");
            MqException ex2 = new MqException("msg2");

            assertEquals("msg1", ex1.getMessage());
            assertEquals("msg2", ex2.getMessage());
        }
    }
}
