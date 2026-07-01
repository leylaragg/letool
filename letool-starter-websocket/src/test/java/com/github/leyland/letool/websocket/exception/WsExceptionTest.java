package com.github.leyland.letool.websocket.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WsException WebSocket 异常测试")
class WsExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(errorCode, message) 构造应正确设置字段")
        void twoArgConstructorShouldSetFields() {
            WsException ex = new WsException("WS_001", "会话不存在");

            assertEquals("WS_001", ex.getErrorCode());
            assertEquals("会话不存在", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(errorCode, message, cause) 构造应正确设置所有字段")
        void threeArgConstructorShouldSetAllFields() {
            RuntimeException cause = new RuntimeException("root cause");
            WsException ex = new WsException("WS_002", "消息发送失败", cause);

            assertEquals("WS_002", ex.getErrorCode());
            assertEquals("消息发送失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承体系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            WsException ex = new WsException("WS_001", "test");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("getErrorCode 测试")
    class GetErrorCodeTests {

        @Test
        @DisplayName("应返回构造时传入的 errorCode")
        void shouldReturnConstructedErrorCode() {
            WsException ex = new WsException("WS_AUTH", "认证失败");
            assertEquals("WS_AUTH", ex.getErrorCode());
        }

        @Test
        @DisplayName("不同异常实例的 errorCode 应独立")
        void errorCodesShouldBeIndependent() {
            WsException ex1 = new WsException("WS_001", "msg1");
            WsException ex2 = new WsException("WS_002", "msg2");
            assertEquals("WS_001", ex1.getErrorCode());
            assertEquals("WS_002", ex2.getErrorCode());
        }
    }
}
