package io.github.leylaragg.letool.mq.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * MQ 模块统一系统异常。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class MqException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 MQ 结构化异常。
     *
     * @param errorCode MQ 稳定错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层异常；没有时传 {@code null}
     */
    private MqException(MqErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的 MQ 异常。
     *
     * @param errorCode MQ 稳定错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化 MQ 异常
     */
    public static MqException of(MqErrorCode errorCode, Object... messageArgs) {
        return new MqException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的 MQ 异常。
     *
     * @param errorCode MQ 稳定错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 保留原因链的结构化 MQ 异常
     */
    public static MqException causedBy(
            MqErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new MqException(errorCode, messageArgs, cause);
    }
}
