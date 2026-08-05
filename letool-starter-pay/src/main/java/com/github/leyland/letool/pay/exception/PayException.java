package com.github.leyland.letool.pay.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 支付模块统一系统异常。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    private PayException(PayErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的支付异常。
     *
     * @param errorCode 支付错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化支付异常
     */
    public static PayException of(PayErrorCode errorCode, Object... messageArgs) {
        return new PayException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的支付异常。
     *
     * @param errorCode 支付错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 结构化支付异常
     */
    public static PayException causedBy(PayErrorCode errorCode, Throwable cause, Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new PayException(errorCode, messageArgs, cause);
    }
}
