package io.github.leylaragg.letool.sms.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 短信模块统一系统异常。
 */
public final class SmsException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建短信异常。
     *
     * @param errorCode 短信错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层原因；没有时可为 {@code null}
     */
    private SmsException(SmsErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的短信异常。
     *
     * @param errorCode 短信错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化短信异常
     */
    public static SmsException of(SmsErrorCode errorCode, Object... messageArgs) {
        return new SmsException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的短信异常。
     *
     * @param errorCode 短信错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 结构化短信异常
     */
    public static SmsException causedBy(
            SmsErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new SmsException(errorCode, messageArgs, cause);
    }
}
