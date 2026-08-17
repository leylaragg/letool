package io.github.leylaragg.letool.monitor.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 监控模块统一系统异常。
 *
 * <p>异常保留稳定错误码、国际化参数和底层原因链，供后台日志与响应边界统一处理。</p>
 */
public final class MonitorException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建监控异常。
     *
     * @param errorCode 监控错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层原因；没有时可为 {@code null}
     */
    private MonitorException(
            MonitorErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的监控异常。
     *
     * @param errorCode 监控错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化监控异常
     */
    public static MonitorException of(
            MonitorErrorCode errorCode,
            Object... messageArgs) {
        return new MonitorException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的监控异常。
     *
     * @param errorCode 监控错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 保留原因链的结构化监控异常
     * @throws IllegalArgumentException 底层异常为空时抛出
     */
    public static MonitorException causedBy(
            MonitorErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new MonitorException(errorCode, messageArgs, cause);
    }
}
