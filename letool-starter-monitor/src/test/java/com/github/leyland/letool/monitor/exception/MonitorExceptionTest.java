package com.github.leyland.letool.monitor.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MonitorException 监控异常测试")
class MonitorExceptionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造")
        void constructorMessage() {
            MonitorException ex = new MonitorException("指标采集失败");
            assertEquals("指标采集失败", ex.getMessage());
            assertNull(ex.getCause());
            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        @DisplayName("(message, cause) 构造")
        void constructorMessageAndCause() {
            RuntimeException cause = new RuntimeException("原始错误");
            MonitorException ex = new MonitorException("告警发送失败", cause);
            assertEquals("告警发送失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }
}
