package com.github.leyland.letool.excel.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * Excel 读写和校验基础设施发生故障时抛出的统一异常。
 *
 * <p>异常仅暴露稳定错误码和安全的默认消息，不会把工作簿内容、
 * 文件路径或底层实现消息拼接到对外消息中。原始异常会通过原因链完整保留，
 * 便于服务端记录日志和排查问题。</p>
 */
public final class ExcelException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建保留底层原因的 Excel 基础设施异常。
     *
     * @param errorCode Excel 稳定错误码
     * @param cause 已校验的底层异常
     */
    private ExcelException(ExcelErrorCode errorCode, Throwable cause) {
        super(errorCode, null, null, cause);
    }

    /**
     * 创建工作簿读取失败异常。
     *
     * @param cause 底层读取或解析异常，不允许为 {@code null}
     * @return 带读取错误码和原始原因链的异常
     * @throws IllegalArgumentException 当 {@code cause} 为 {@code null} 时抛出
     */
    public static ExcelException readFailed(Throwable cause) {
        return new ExcelException(ExcelErrorCode.READ_FAILED, requireCause(cause));
    }

    /**
     * 创建工作簿写入失败异常。
     *
     * @param cause 底层生成或写出异常，不允许为 {@code null}
     * @return 带写入错误码和原始原因链的异常
     * @throws IllegalArgumentException 当 {@code cause} 为 {@code null} 时抛出
     */
    public static ExcelException writeFailed(Throwable cause) {
        return new ExcelException(ExcelErrorCode.WRITE_FAILED, requireCause(cause));
    }

    /**
     * 创建数据校验执行失败异常。
     *
     * @param cause 底层反射或规则异常，不允许为 {@code null}
     * @return 带校验错误码和原始原因链的异常
     * @throws IllegalArgumentException 当 {@code cause} 为 {@code null} 时抛出
     */
    public static ExcelException validationFailed(Throwable cause) {
        return new ExcelException(ExcelErrorCode.VALIDATION_FAILED, requireCause(cause));
    }

    /**
     * 校验需要保留的底层异常。
     *
     * @param cause 底层异常
     * @return 已校验的底层异常
     * @throws IllegalArgumentException 当 {@code cause} 为 {@code null} 时抛出
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
