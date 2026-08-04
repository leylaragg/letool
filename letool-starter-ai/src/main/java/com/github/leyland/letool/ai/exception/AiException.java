package com.github.leyland.letool.ai.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * AI 模块统一结构化系统异常。
 *
 * <p>异常只描述 Letool 模型路由和客户端定制故障。具体模型调用产生的 Spring AI
 * 异常会保持原样传播，便于业务项目继续使用 Provider 提供的诊断信息和重试策略。</p>
 */
public final class AiException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建结构化 AI 系统异常。
     *
     * @param errorCode AI 模块错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层原因；没有底层原因时可为 {@code null}
     */
    private AiException(
            AiErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的结构化 AI 异常。
     *
     * @param errorCode AI 模块错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化 AI 异常
     */
    public static AiException of(AiErrorCode errorCode, Object... messageArgs) {
        return new AiException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的结构化 AI 异常。
     *
     * @param errorCode AI 模块错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 保留底层原因链的结构化 AI 异常
     * @throws IllegalArgumentException 底层异常为空时抛出
     */
    public static AiException causedBy(
            AiErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("底层异常不能为空");
        }
        return new AiException(errorCode, messageArgs, cause);
    }
}
