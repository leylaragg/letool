package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 调用方输出目标拒绝写入或刷新时抛出的系统异常。
 *
 * <p>底层消息只保留在原因链中，公开错误不会暴露调用方路径或存储细节。</p>
 *
 * @author leyland
 */
public final class PrintOutputException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建保留底层原因的输出异常。
     *
     * @param cause 调用方输出目标抛出的非空异常
     */
    private PrintOutputException(Throwable cause) {
        super(PrintErrorCode.OUTPUT_WRITE_FAILED, new Object[0], null, cause);
    }

    /**
     * @param cause 调用方输出目标抛出的非空异常
     * @return 不回显底层消息的输出异常
     */
    public static PrintOutputException writeFailed(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new PrintOutputException(cause);
    }
}
