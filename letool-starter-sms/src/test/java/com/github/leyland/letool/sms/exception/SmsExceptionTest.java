package com.github.leyland.letool.sms.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmsException 短信异常测试")
class SmsExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造函数应正确设置消息")
        void messageConstructorShouldSetMessage() {
            SmsException ex = new SmsException("短信发送失败");

            assertEquals("短信发送失败", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause) 构造函数应设置消息和原因")
        void messageCauseConstructorShouldSetBoth() {
            Throwable cause = new RuntimeException("网络错误");
            SmsException ex = new SmsException("调用失败", cause);

            assertEquals("调用失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            SmsException ex = new SmsException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }
}
