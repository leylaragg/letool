package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 宿主业务数据适配器发生未分类技术故障时抛出的安全异常。
 *
 * <p>原始异常只保留在原因链中，业务值和第三方消息不会进入公开消息参数。</p>
 *
 * @author leyland
 */
public final class PrintAdapterException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建保留底层原因的适配器异常。 */
    private PrintAdapterException(Throwable cause) {
        super(
                PrintErrorCode.ADAPTER_EXECUTION_FAILED,
                new Object[]{"业务数据准备未完成"},
                null,
                cause);
    }

    /**
     * 将未知适配器故障转换为稳定打印异常。
     *
     * @param cause 非空底层异常
     * @return 不回显底层消息的适配器异常
     * @throws IllegalArgumentException 原因为空时抛出
     */
    public static PrintAdapterException executionFailed(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new PrintAdapterException(cause);
    }
}
