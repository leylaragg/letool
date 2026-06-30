package com.github.leyland.letool.mail.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MailResponse 邮件响应模型单元测试。
 *
 * @author leyland
 */
@DisplayName("MailResponse 邮件响应模型测试")
class MailResponseTest {

    // ======================== 成功响应 ========================

    @Nested
    @DisplayName("success 工厂方法")
    class SuccessFactoryTests {

        @Test
        @DisplayName("应创建成功响应并设置 messageId")
        void shouldCreateSuccessResponse() {
            MailResponse response = MailResponse.success("<msg-123@mail.example.com>");
            assertTrue(response.isSuccess());
            assertEquals("<msg-123@mail.example.com>", response.getMessageId());
            assertNull(response.getError(), "成功时 error 应为 null");
        }

        @Test
        @DisplayName("成功响应的 sendTime 应接近当前时间")
        void shouldHaveSendTimeCloseToNow() {
            LocalDateTime before = LocalDateTime.now();
            MailResponse response = MailResponse.success("msg-001");
            LocalDateTime after = LocalDateTime.now();

            assertNotNull(response.getSendTime());
            assertFalse(response.getSendTime().isBefore(before.minus(1, ChronoUnit.SECONDS)),
                    "sendTime 不应早于调用前 1 秒");
            assertFalse(response.getSendTime().isAfter(after.plus(1, ChronoUnit.SECONDS)),
                    "sendTime 不应晚于调用后 1 秒");
        }

        @Test
        @DisplayName("多次调用 success 应生成不同实例")
        void shouldCreateDifferentInstances() {
            MailResponse r1 = MailResponse.success("id-1");
            MailResponse r2 = MailResponse.success("id-2");
            assertNotSame(r1, r2);
            assertNotEquals(r1.getMessageId(), r2.getMessageId());
        }
    }

    // ======================== 失败响应 ========================

    @Nested
    @DisplayName("fail 工厂方法")
    class FailFactoryTests {

        @Test
        @DisplayName("应创建失败响应并设置 error 消息")
        void shouldCreateFailResponse() {
            MailResponse response = MailResponse.fail("SMTP 连接超时");
            assertFalse(response.isSuccess());
            assertEquals("SMTP 连接超时", response.getError());
            assertNull(response.getMessageId(), "失败时 messageId 应为 null");
        }

        @Test
        @DisplayName("失败响应的 sendTime 应不为 null")
        void shouldHaveNonNullSendTime() {
            MailResponse response = MailResponse.fail("认证失败");
            assertNotNull(response.getSendTime());
        }

        @Test
        @DisplayName("多次调用 fail 应生成不同实例")
        void shouldCreateDifferentInstances() {
            MailResponse r1 = MailResponse.fail("错误1");
            MailResponse r2 = MailResponse.fail("错误2");
            assertNotSame(r1, r2);
            assertNotEquals(r1.getError(), r2.getError());
        }
    }

    // ======================== 业务逻辑验证 ========================

    @Nested
    @DisplayName("业务逻辑验证")
    class BusinessLogicTests {

        @Test
        @DisplayName("成功响应 isSuccess 应为 true 且 error 为 null")
        void successResponseShouldHaveNullError() {
            MailResponse response = MailResponse.success("msg-123");
            assertTrue(response.isSuccess());
            assertNotNull(response.getMessageId());
            assertNull(response.getError());
        }

        @Test
        @DisplayName("失败响应 isSuccess 应为 false 且 messageId 为 null")
        void failResponseShouldHaveNullMessageId() {
            MailResponse response = MailResponse.fail("发送失败");
            assertFalse(response.isSuccess());
            assertNotNull(response.getError());
            assertNull(response.getMessageId());
        }
    }
}
