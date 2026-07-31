package com.github.leyland.letool.ratelimiter.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 限流模块对外暴露的稳定错误码。
 */
public enum RateLimitErrorCode implements ErrorCode {

    /**
     * 请求被限流策略拒绝。
     */
    REQUEST_REJECTED("RATE_LIMIT_001", "限流策略 {0} 拒绝了本次请求"),

    /**
     * 限流配置不合法。
     */
    CONFIGURATION_INVALID("RATE_LIMIT_002", "限流配置不合法：{0}"),

    /**
     * 声明式限流回退方法配置不合法。
     */
    FALLBACK_METHOD_INVALID("RATE_LIMIT_003", "限流回退方法配置不合法：{0}");

    /**
     * 稳定的机器可读错误码。
     */
    private final String code;

    /**
     * 未配置国际化文案时使用的默认消息模板。
     */
    private final String defaultMessage;

    /**
     * 创建限流错误码。
     *
     * @param code           稳定错误码
     * @param defaultMessage 默认消息模板
     */
    RateLimitErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 稳定错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
