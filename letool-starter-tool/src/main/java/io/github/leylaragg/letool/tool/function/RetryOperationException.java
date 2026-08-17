package io.github.leylaragg.letool.tool.function;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 重试策略校验、任务失败、次数耗尽或线程中断时抛出的统一异常。
 *
 * <p>异常只在默认消息中记录安全的参数名称和尝试次数，不记录任务返回值、
 * 原始异常消息或业务参数。底层原因仍保留在异常链中供受控诊断使用。</p>
 */
public final class RetryOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建重试统一异常。
     *
     * @param errorCode 重试稳定错误码
     * @param messageArgs 安全消息参数
     * @param cause 底层失败原因，允许为空
     */
    private RetryOperationException(
            RetryErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建参数无效异常。
     *
     * @param parameterName 安全的参数名称，不得包含实际参数值
     * @return 参数异常
     */
    public static RetryOperationException invalidArgument(String parameterName) {
        String safeName = parameterName == null || parameterName.isBlank()
                ? "unknown"
                : parameterName;
        return new RetryOperationException(
                RetryErrorCode.INVALID_ARGUMENT,
                new Object[]{safeName},
                new IllegalArgumentException("Invalid retry argument: " + safeName)
        );
    }

    /**
     * 创建任务执行失败异常。
     *
     * @param attempts 已执行次数
     * @param cause 最后一次任务异常
     * @return 执行失败异常
     */
    public static RetryOperationException executionFailed(int attempts, Throwable cause) {
        return new RetryOperationException(
                RetryErrorCode.EXECUTION_FAILED,
                new Object[]{attempts},
                cause
        );
    }

    /**
     * 创建最大尝试次数耗尽异常。
     *
     * @param attempts 已执行次数
     * @param cause 最后一次异常或结果耗尽异常
     * @return 次数耗尽异常
     */
    public static RetryOperationException exhausted(int attempts, Throwable cause) {
        return new RetryOperationException(
                RetryErrorCode.EXHAUSTED,
                new Object[]{attempts},
                cause
        );
    }

    /**
     * 创建重试执行中断异常。
     *
     * @param attempts 中断前已经执行的次数
     * @param cause 中断原因
     * @return 中断异常
     */
    public static RetryOperationException interrupted(int attempts, Throwable cause) {
        return new RetryOperationException(
                RetryErrorCode.INTERRUPTED,
                new Object[]{attempts},
                cause
        );
    }
}
