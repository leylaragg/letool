package com.github.leyland.letool.tool.value;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 基础值、集合和枚举操作不符合稳定契约时抛出的统一异常。
 */
public final class ValueOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建基础值统一异常。
     *
     * @param errorCode 基础值稳定错误码
     * @param subject 可安全公开的参数或属性名称
     * @param cause 底层异常；没有底层异常时可传 {@code null}
     */
    private ValueOperationException(
            ValueErrorCode errorCode,
            String subject,
            Throwable cause) {
        super(errorCode, new Object[]{safe(subject)}, null, cause);
    }

    /**
     * 创建参数不符合契约异常。
     *
     * @param parameterName 安全的参数名称
     * @return 参数异常
     */
    public static ValueOperationException invalidArgument(String parameterName) {
        String safeName = safe(parameterName);
        return new ValueOperationException(
                ValueErrorCode.INVALID_ARGUMENT,
                safeName,
                new IllegalArgumentException("Invalid value argument: " + safeName)
        );
    }

    /**
     * 创建枚举属性读取失败异常。
     *
     * @param propertyName 安全的枚举属性名称
     * @param cause 底层读取异常
     * @return 枚举属性读取异常
     */
    public static ValueOperationException enumAccessFailed(
            String propertyName,
            Throwable cause) {
        Throwable safeCause = cause == null
                ? new IllegalStateException("Enum property access failed")
                : cause;
        return new ValueOperationException(
                ValueErrorCode.ENUM_ACCESS_FAILED,
                propertyName,
                safeCause
        );
    }

    /**
     * 创建严格枚举查询未命中异常。
     *
     * @param enumName 安全的枚举类型名称
     * @return 枚举常量不存在异常
     */
    public static ValueOperationException enumConstantNotFound(String enumName) {
        return new ValueOperationException(
                ValueErrorCode.ENUM_CONSTANT_NOT_FOUND,
                enumName,
                null
        );
    }

    /**
     * 创建枚举描述重复异常。
     *
     * @param propertyName 安全的描述属性名称
     * @return 枚举描述重复异常
     */
    public static ValueOperationException duplicateEnumLabel(String propertyName) {
        return new ValueOperationException(
                ValueErrorCode.DUPLICATE_ENUM_LABEL,
                propertyName,
                null
        );
    }

    /**
     * 规范化公开消息中的安全名称。
     *
     * @param value 待规范化名称
     * @return 非空且非空白的安全名称
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
