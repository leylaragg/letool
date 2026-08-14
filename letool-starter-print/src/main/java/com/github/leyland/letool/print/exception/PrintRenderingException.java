package com.github.leyland.letool.print.exception;

import com.github.leyland.letool.exception.core.SystemException;
import com.github.leyland.letool.print.api.OutputFormat;

import java.io.Serial;

/**
 * 文档渲染失败或渲染产物越过治理边界时抛出的系统异常。
 *
 * <p>底层渲染器的原始消息只保留在原因链中，不会成为用户可见的消息参数。</p>
 *
 * @author leyland
 */
public final class PrintRenderingException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建带安全消息参数的渲染异常。 */
    private PrintRenderingException(PrintErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * @param format 渲染失败的目标格式
     * @param cause 非空底层异常
     * @return 保留原因链的渲染异常
     */
    public static PrintRenderingException renderFailed(OutputFormat format, Throwable cause) {
        requireCause(cause);
        return new PrintRenderingException(
                PrintErrorCode.RENDERING_FAILED,
                new Object[]{format.value()},
                cause);
    }

    /**
     * @param maxPages 请求声明的最大页数
     * @return 页数超限异常
     */
    public static PrintRenderingException pageLimitExceeded(int maxPages) {
        return new PrintRenderingException(
                PrintErrorCode.PAGE_LIMIT_EXCEEDED,
                new Object[]{maxPages},
                null);
    }

    /**
     * @param maxBytes 请求声明的最大产物字节数
     * @param cause 触发写入中断的非空底层异常
     * @return 产物超限异常
     */
    public static PrintRenderingException outputLimitExceeded(long maxBytes, Throwable cause) {
        requireCause(cause);
        return new PrintRenderingException(
                PrintErrorCode.OUTPUT_LIMIT_EXCEEDED,
                new Object[]{maxBytes},
                cause);
    }

    /** 确保系统故障始终能沿原因链追溯。 */
    private static void requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
    }
}
