package com.github.leyland.letool.tool.date;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 日期时间参数校验、解析、格式化或转换失败时抛出的统一异常。
 *
 * <p>异常保留稳定错误码和原始原因链，但解析异常不会把原始日期文本写入默认消息，
 * 避免业务标识或其他输入内容进入普通日志和对外响应。</p>
 */
public final class DateOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建日期时间统一异常。
     *
     * @param errorCode 日期时间稳定错误码
     * @param messageArgs 安全消息参数，允许为空
     * @param cause 底层失败原因
     */
    private DateOperationException(DateErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建参数不符合契约异常。
     *
     * @param parameterName 安全的参数名称，不得包含实际参数值
     * @return 参数异常
     */
    public static DateOperationException invalidArgument(String parameterName) {
        String safeName = parameterName == null || parameterName.isBlank() ? "unknown" : parameterName;
        return new DateOperationException(
                DateErrorCode.INVALID_ARGUMENT,
                new Object[]{safeName},
                new IllegalArgumentException("Invalid date-time argument: " + safeName)
        );
    }

    /**
     * 创建严格解析失败异常。
     *
     * @param cause JDK 日期时间解析异常
     * @return 解析异常
     */
    public static DateOperationException parseFailed(Throwable cause) {
        return new DateOperationException(DateErrorCode.PARSE_FAILED, null, requireCause(cause));
    }

    /**
     * 创建格式化失败异常。
     *
     * @param cause JDK 日期时间格式化异常
     * @return 格式化异常
     */
    public static DateOperationException formatFailed(Throwable cause) {
        return new DateOperationException(DateErrorCode.FORMAT_FAILED, null, requireCause(cause));
    }

    /**
     * 创建日期时间转换失败异常。
     *
     * @param cause JDK 日期时间转换异常
     * @return 转换异常
     */
    public static DateOperationException conversionFailed(Throwable cause) {
        return new DateOperationException(DateErrorCode.CONVERSION_FAILED, null, requireCause(cause));
    }

    /**
     * 校验必须保留的底层异常原因。
     *
     * @param cause 底层异常
     * @return 校验通过的异常
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
