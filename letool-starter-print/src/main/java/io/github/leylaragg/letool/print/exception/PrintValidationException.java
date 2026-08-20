package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.core.BusinessException;

import java.io.Serial;

/**
 * 打印请求、上下文或文档模型违反公开契约时抛出的业务异常。
 *
 * @author leyland
 */
public final class PrintValidationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建打印校验异常。 */
    private PrintValidationException(
            PrintErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建请求校验异常。
     *
     * @param detail 不包含敏感数据的错误详情
     * @return 请求校验异常
     */
    public static PrintValidationException invalidRequest(String detail) {
        return new PrintValidationException(
                PrintErrorCode.INVALID_REQUEST,
                new Object[]{requireDetail(detail)},
                null);
    }

    /**
     * 创建保留内部原因的请求校验异常。
     *
     * @param detail 不包含敏感数据的错误详情
     * @param cause 只供受控日志排查的底层原因
     * @return 请求校验异常
     */
    public static PrintValidationException invalidRequest(String detail, Throwable cause) {
        return new PrintValidationException(
                PrintErrorCode.INVALID_REQUEST,
                new Object[]{requireDetail(detail)},
                requireCause(cause));
    }

    /**
     * 创建文档模型校验异常。
     *
     * @param detail 不包含业务正文的错误详情
     * @return 文档模型校验异常
     */
    public static PrintValidationException invalidDocument(String detail) {
        return new PrintValidationException(
                PrintErrorCode.INVALID_DOCUMENT,
                new Object[]{requireDetail(detail)},
                null);
    }

    /**
     * 创建保留内部原因的文档模型校验异常。
     *
     * @param detail 不包含业务正文的错误详情
     * @param cause 只供受控日志排查的底层原因
     * @return 文档模型校验异常
     */
    public static PrintValidationException invalidDocument(String detail, Throwable cause) {
        return new PrintValidationException(
                PrintErrorCode.INVALID_DOCUMENT,
                new Object[]{requireDetail(detail)},
                requireCause(cause));
    }

    /** 校验可安全展示的详情。 */
    private static String requireDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail 不能为空");
        }
        return detail;
    }

    /** 保证原因链确实包含可排查的异常。 */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return cause;
    }
}
