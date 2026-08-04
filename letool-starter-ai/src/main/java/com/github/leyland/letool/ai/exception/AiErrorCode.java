package com.github.leyland.letool.ai.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * AI 模块稳定错误码。
 *
 * <p>错误码标识供程序判断和外部错误协议使用，默认中文消息用于日志兜底，
 * 其中的占位符遵循 {@link java.text.MessageFormat} 语法。</p>
 */
public enum AiErrorCode implements ErrorCode {

    /** AI 配置不合法。 */
    CONFIGURATION_INVALID("AI_CONFIGURATION_INVALID", "AI 配置不合法：{0}"),

    /** 未找到指定的对话模型。 */
    CHAT_MODEL_NOT_FOUND("AI_CHAT_MODEL_NOT_FOUND", "未找到 ChatModel：{0}"),

    /** 未找到指定的嵌入模型。 */
    EMBEDDING_MODEL_NOT_FOUND("AI_EMBEDDING_MODEL_NOT_FOUND", "未找到 EmbeddingModel：{0}"),

    /** AI 客户端定制器执行失败。 */
    CLIENT_CUSTOMIZATION_FAILED("AI_CLIENT_CUSTOMIZATION_FAILED", "AI 客户端定制失败：{0}");

    /** 稳定的外部错误码标识。 */
    private final String code;

    /** 不依赖 Spring 上下文的默认中文消息模板。 */
    private final String defaultMessage;

    /**
     * 创建 AI 错误码。
     *
     * @param code 稳定错误码标识
     * @param defaultMessage 默认中文消息模板
     */
    AiErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码标识。
     *
     * @return 稳定错误码标识
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认中文消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
