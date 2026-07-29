package com.github.leyland.letool.data.exception;

import com.github.leyland.letool.exception.code.ErrorCode;
import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 数据访问层的系统异常。
 *
 * <p>该类型保留数据模块原有的字符串错误码构造方式，并将其适配为统一的
 * {@link ErrorCode} 模型。异常消息在构造时固定为“错误码加默认消息”的形式，
 * 因而即使脱离 Web 请求上下文，使用 {@code log.error("...", exception)}
 * 记录时也能稳定输出错误码、默认消息、堆栈和异常原因链。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DataException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 使用错误码和默认消息构造数据异常。
     *
     * @param errorCode 稳定的错误码，例如 {@code DATA_001}
     * @param message 无国际化资源时使用的默认消息
     * @throws IllegalArgumentException 当 {@code errorCode} 或 {@code message}
     *         为 {@code null}、空串或仅含空白字符时抛出
     */
    public DataException(String errorCode, String message) {
        super(ErrorCode.of(errorCode, message), null, null, null);
    }

    /**
     * 使用错误码、默认消息和底层原因构造数据异常。
     *
     * @param errorCode 稳定的错误码，例如 {@code DATA_500}
     * @param message 无国际化资源时使用的默认消息
     * @param cause 需要保留的底层异常；允许为 {@code null} 以兼容原构造语义
     * @throws IllegalArgumentException 当 {@code errorCode} 或 {@code message}
     *         为 {@code null}、空串或仅含空白字符时抛出
     */
    public DataException(String errorCode, String message, Throwable cause) {
        super(ErrorCode.of(errorCode, message), null, null, cause);
    }
}
