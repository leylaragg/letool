package io.github.leylaragg.letool.exception.core;

import io.github.leylaragg.letool.exception.code.ErrorCode;

import java.io.Serial;

/**
 * 表示调用方可以处理、不会造成服务中断的预期业务或流程拒绝。
 */
public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建支持扩展的业务异常，可使用国际化参数或显式文本。
     *
     * @param errorCode 结构化领域错误码
     * @param messageArgs 为响应渲染保留的国际化模板参数
     * @param customMessage 不参与国际化的显式消息；使用错误码模板时传 {@code null}
     * @param cause 关联的底层异常；业务拒绝没有底层异常时传 {@code null}
     */
    protected BusinessException(
            ErrorCode errorCode,
            Object[] messageArgs,
            String customMessage,
            Throwable cause) {
        super(errorCode, messageArgs, customMessage, cause);
    }

    /**
     * 创建保留消息参数、可在响应阶段国际化的业务异常。
     *
     * @param errorCode 结构化领域错误码及默认消息模板
     * @param messageArgs 同时用于稳定默认消息和国际化响应的参数
     * @return 不含底层异常和自定义消息的业务异常
     */
    public static BusinessException of(ErrorCode errorCode, Object... messageArgs) {
        return new BusinessException(errorCode, messageArgs, null, null);
    }

    /**
     * 创建使用显式文本并主动跳过国际化的业务异常。
     *
     * @param errorCode 用于诊断的结构化领域错误码
     * @param customMessage 要保留的非空白精确文本
     * @return 不含格式化参数和底层异常的业务异常
     * @throws IllegalArgumentException 当 {@code customMessage} 为 {@code null} 或空白时抛出
     */
    public static BusinessException custom(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, null, requireCustomMessage(customMessage), null);
    }

    /**
     * 创建可国际化且保留底层原因的业务异常。
     *
     * @param errorCode 结构化领域错误码及默认消息模板
     * @param cause 必填的底层异常，用于记录完整异常日志
     * @param messageArgs 同时用于稳定默认消息和国际化响应的参数
     * @return 带完整异常原因链的业务异常
     * @throws IllegalArgumentException 当 {@code cause} 为 {@code null} 时抛出
     */
    public static BusinessException causedBy(
            ErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        return new BusinessException(errorCode, messageArgs, null, requireCause(cause));
    }

    private static String requireCustomMessage(String customMessage) {
        if (customMessage == null || customMessage.isBlank()) {
            throw new IllegalArgumentException("customMessage must not be blank");
        }
        return customMessage;
    }

    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
