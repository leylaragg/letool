package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.core.SystemException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.TemplateFormat;

import java.io.Serial;

/**
 * 打印管线注册、路由和执行失败时抛出的系统异常。
 *
 * <p>第三方原始消息不会进入用户可见参数，只通过原因链供受控日志排查。</p>
 *
 * @author leyland
 */
public final class PrintPipelineException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建打印管线异常。 */
    private PrintPipelineException(
            PrintErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * @param format 未找到管线的模板格式
     * @return 管线不存在异常
     */
    public static PrintPipelineException notFound(TemplateFormat format) {
        return create(PrintErrorCode.PIPELINE_NOT_FOUND, format.value());
    }

    /**
     * @param format 不受支持的输出格式
     * @return 输出格式不受支持异常
     */
    public static PrintPipelineException outputNotSupported(OutputFormat format) {
        return create(PrintErrorCode.OUTPUT_NOT_SUPPORTED, format.value());
    }

    /**
     * @param format 重复注册的模板格式
     * @return 重复模板格式注册异常
     */
    public static PrintPipelineException duplicate(TemplateFormat format) {
        return create(PrintErrorCode.DUPLICATE_PIPELINE, format.value());
    }

    /**
     * @param detail 安全的注册错误详情
     * @return 非法管线注册异常
     */
    public static PrintPipelineException invalidRegistration(String detail) {
        return create(PrintErrorCode.INVALID_PIPELINE_REGISTRATION, detail);
    }

    /**
     * 创建保留底层原因的管线执行异常。
     *
     * @param format 发生故障的模板格式
     * @param cause 非空底层异常
     * @return 管线执行异常
     */
    public static PrintPipelineException executionFailed(
            TemplateFormat format,
            Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new PrintPipelineException(
                PrintErrorCode.PIPELINE_EXECUTION_FAILED,
                new Object[]{format.value()},
                cause);
    }

    /**
     * @param maxBytes 请求声明的最大产物字节数
     * @return 产物超限异常
     */
    public static PrintPipelineException outputLimitExceeded(long maxBytes) {
        return create(PrintErrorCode.OUTPUT_LIMIT_EXCEEDED, maxBytes);
    }

    /** 创建没有底层原因的打印管线异常。 */
    private static PrintPipelineException create(PrintErrorCode errorCode, Object argument) {
        return new PrintPipelineException(errorCode, new Object[]{argument}, null);
    }
}
