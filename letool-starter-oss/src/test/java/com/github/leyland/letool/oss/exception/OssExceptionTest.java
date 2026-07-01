package com.github.leyland.letool.oss.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OssException OSS 对象存储异常测试")
class OssExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造函数应正确设置消息")
        void messageConstructorShouldSetMessage() {
            OssException ex = new OssException("上传失败");

            assertEquals("上传失败", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause) 构造函数应设置消息和原因")
        void messageCauseConstructorShouldSetBoth() {
            Throwable cause = new RuntimeException("网络错误");
            OssException ex = new OssException("上传失败", cause);

            assertEquals("上传失败", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("(cause) 构造函数应仅设置原因")
        void causeConstructorShouldSetCause() {
            Throwable cause = new RuntimeException("网络错误");
            OssException ex = new OssException(cause);

            assertEquals("java.lang.RuntimeException: 网络错误", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            OssException ex = new OssException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }
}
