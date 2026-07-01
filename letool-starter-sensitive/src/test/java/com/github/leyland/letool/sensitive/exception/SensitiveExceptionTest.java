package com.github.leyland.letool.sensitive.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveException 测试")
class SensitiveExceptionTest {

    @Test
    @DisplayName("message 构造应设置 message")
    void shouldSetMessage() {
        SensitiveException ex = new SensitiveException("脱敏失败");
        assertEquals("脱敏失败", ex.getMessage());
    }

    @Test
    @DisplayName("message + cause 构造应设置两者")
    void shouldSetMessageAndCause() {
        RuntimeException cause = new RuntimeException("原始错误");
        SensitiveException ex = new SensitiveException("脱敏失败", cause);

        assertEquals("脱敏失败", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("应为 RuntimeException 子类")
    void shouldBeRuntimeException() {
        SensitiveException ex = new SensitiveException("test");
        assertTrue(ex instanceof RuntimeException);
    }
}
