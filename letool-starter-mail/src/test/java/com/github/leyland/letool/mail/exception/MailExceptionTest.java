package com.github.leyland.letool.mail.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailException 邮件异常类单元测试。
 *
 * @author leyland
 */
@DisplayName("MailException 邮件异常测试")
class MailExceptionTest {

    // ======================== 构造方法测试 ========================

    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("单参数构造方法应正确设置消息")
        void shouldSetMessageWithSingleArgConstructor() {
            MailException ex = new MailException("SMTP 服务器连接失败");
            assertEquals("SMTP 服务器连接失败", ex.getMessage());
            assertNull(ex.getCause(), "无 cause 时 getCause 应为 null");
        }

        @Test
        @DisplayName("双参数构造方法应正确设置消息和原因")
        void shouldSetMessageAndCauseWithTwoArgConstructor() {
            IOException cause = new IOException("网络不可达");
            MailException ex = new MailException("邮件发送失败", cause);
            assertEquals("邮件发送失败", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertEquals("网络不可达", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("双参数构造方法应正确传递嵌套原因链")
        void shouldPreserveCauseChain() {
            IOException rootCause = new IOException("连接重置");
            RuntimeException intermediate = new RuntimeException("包装异常", rootCause);
            MailException ex = new MailException("最终失败", intermediate);

            assertSame(intermediate, ex.getCause());
            assertSame(rootCause, ex.getCause().getCause());
        }
    }

    // ======================== 继承关系测试 ========================

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("MailException 应是 RuntimeException 的子类")
        void shouldBeRuntimeExceptionSubclass() {
            MailException ex = new MailException("测试");
            assertTrue(ex instanceof RuntimeException,
                    "MailException 应继承自 RuntimeException（非受检异常）");
        }

        @Test
        @DisplayName("MailException 应可被抛出和捕获而无需声明 throws")
        void shouldBeUncheckedException() {
            // MailException 继承自 RuntimeException，为免检异常
            // 此处直接 throw 并 catch 来验证其免检特性
            try {
                throw new MailException("运行时异常");
            } catch (MailException e) {
                assertEquals("运行时异常", e.getMessage());
                assertTrue(e instanceof RuntimeException,
                        "MailException 应继承自 RuntimeException（免检异常）");
            }
        }
    }

    // ======================== 异常消息测试 ========================

    @Nested
    @DisplayName("异常消息内容测试")
    class ExceptionMessageTests {

        @Test
        @DisplayName("中文错误消息应正确存储")
        void shouldStoreChineseErrorMessage() {
            MailException ex = new MailException("发件人地址不能为空");
            assertEquals("发件人地址不能为空", ex.getMessage());
        }

        @Test
        @DisplayName("带占位符的错误消息应正确存储")
        void shouldStoreErrorMessageWithPlaceholders() {
            MailException ex = new MailException("发送邮件到 user@example.com 失败: 连接超时");
            assertTrue(ex.getMessage().contains("user@example.com"));
            assertTrue(ex.getMessage().contains("连接超时"));
        }
    }
}
