package io.github.leylaragg.letool.ai.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AiException} 结构化错误语义测试。
 */
@DisplayName("AiException 结构化异常测试")
class AiExceptionTest {

    /**
     * 验证 AI 模块错误码具有稳定标识和默认中文消息。
     */
    @Test
    @DisplayName("AI 错误码定义稳定")
    void shouldDefineStableErrorCodes() {
        assertErrorCode(
                AiErrorCode.CONFIGURATION_INVALID,
                "AI_CONFIGURATION_INVALID",
                "AI 配置不合法：{0}");
        assertErrorCode(
                AiErrorCode.CHAT_MODEL_NOT_FOUND,
                "AI_CHAT_MODEL_NOT_FOUND",
                "未找到 ChatModel：{0}");
        assertErrorCode(
                AiErrorCode.EMBEDDING_MODEL_NOT_FOUND,
                "AI_EMBEDDING_MODEL_NOT_FOUND",
                "未找到 EmbeddingModel：{0}");
        assertErrorCode(
                AiErrorCode.CLIENT_CUSTOMIZATION_FAILED,
                "AI_CLIENT_CUSTOMIZATION_FAILED",
                "AI 客户端定制失败：{0}");
    }

    /**
     * 验证无底层原因的工厂方法保留错误码和消息参数。
     */
    @Test
    @DisplayName("of 保留结构化错误码和消息参数")
    void shouldRetainErrorCodeAndArguments() {
        AiException exception = AiException.of(
                AiErrorCode.CHAT_MODEL_NOT_FOUND,
                "primary");

        assertSame(AiErrorCode.CHAT_MODEL_NOT_FOUND, exception.getErrorCode());
        assertArrayEquals(new Object[]{"primary"}, exception.getMessageArgs());
        assertNull(exception.getCause());
    }

    /**
     * 验证带底层原因的工厂方法完整保留原因链。
     */
    @Test
    @DisplayName("causedBy 保留底层原因")
    void shouldRetainCause() {
        IllegalStateException cause = new IllegalStateException("定制失败");

        AiException exception = AiException.causedBy(
                AiErrorCode.CLIENT_CUSTOMIZATION_FAILED,
                cause,
                "primary");

        assertSame(AiErrorCode.CLIENT_CUSTOMIZATION_FAILED, exception.getErrorCode());
        assertArrayEquals(new Object[]{"primary"}, exception.getMessageArgs());
        assertSame(cause, exception.getCause());
    }

    /**
     * 验证创建带原因异常时拒绝空原因。
     */
    @Test
    @DisplayName("causedBy 拒绝空原因")
    void shouldRejectNullCause() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AiException.causedBy(
                        AiErrorCode.CLIENT_CUSTOMIZATION_FAILED,
                        null,
                        "primary"));
    }

    /**
     * 断言单个错误码的稳定标识与默认消息。
     *
     * @param errorCode 待验证的 AI 错误码
     * @param code 期望的稳定错误码标识
     * @param defaultMessage 期望的默认中文消息
     */
    private static void assertErrorCode(
            AiErrorCode errorCode,
            String code,
            String defaultMessage) {
        assertEquals(code, errorCode.getCode());
        assertEquals(defaultMessage, errorCode.getDefaultMessage());
    }
}
