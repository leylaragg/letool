package com.github.leyland.letool.ratelimiter.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 限流配置或声明式回退方法不合法时抛出的统一系统异常。
 */
public final class RateLimitConfigurationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建限流配置异常。
     *
     * @param errorCode   限流错误码
     * @param messageArgs 安全的消息参数
     */
    private RateLimitConfigurationException(RateLimitErrorCode errorCode, Object[] messageArgs) {
        super(errorCode, messageArgs, null, null);
    }

    /**
     * 创建普通配置错误。
     *
     * @param field 不合法的配置字段
     * @return 限流配置异常
     */
    public static RateLimitConfigurationException invalid(String field) {
        return new RateLimitConfigurationException(
                RateLimitErrorCode.CONFIGURATION_INVALID,
                new Object[]{requireValue(field, "field")}
        );
    }

    /**
     * 创建回退方法配置错误。
     *
     * @param methodName 回退方法名称
     * @return 限流配置异常
     */
    public static RateLimitConfigurationException invalidFallback(String methodName) {
        return new RateLimitConfigurationException(
                RateLimitErrorCode.FALLBACK_METHOD_INVALID,
                new Object[]{requireValue(methodName, "methodName")}
        );
    }

    /**
     * 校验异常消息参数。
     *
     * @param value     参数值
     * @param fieldName 参数名称
     * @return 已校验参数
     */
    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
