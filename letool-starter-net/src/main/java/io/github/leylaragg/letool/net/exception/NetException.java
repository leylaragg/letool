package io.github.leylaragg.letool.net.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 网络模块统一系统异常。
 *
 * <p>异常只保留结构化错误码和必要诊断参数，不会自动拼接底层异常消息或业务报文，
 * 避免在日志及 HTTP 响应中泄露敏感数据。</p>
 */
public final class NetException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建网络异常。
     *
     * @param errorCode 网络错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层原因；没有时可为 {@code null}
     */
    private NetException(
            NetErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的网络异常。
     *
     * @param errorCode 网络错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化网络异常
     */
    public static NetException of(NetErrorCode errorCode, Object... messageArgs) {
        return new NetException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的网络异常。
     *
     * @param errorCode 网络错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 保留原因链的结构化网络异常
     * @throws IllegalArgumentException 底层异常为空时抛出
     */
    public static NetException causedBy(
            NetErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new NetException(errorCode, messageArgs, cause);
    }
}
