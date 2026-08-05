package com.github.leyland.letool.sensitive.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 脱敏模块稳定错误码。
 */
public enum SensitiveErrorCode implements ErrorCode {

    /** 脱敏配置不满足约束。 */
    CONFIGURATION_INVALID("SENSITIVE_001", "脱敏配置无效：{0}"),

    /** 指定类型没有可用策略。 */
    STRATEGY_NOT_FOUND("SENSITIVE_002", "未找到脱敏策略：{0}"),

    /** 脱敏策略执行失败。 */
    MASK_FAILED("SENSITIVE_003", "脱敏策略执行失败：{0}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建脱敏错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认消息模板
     */
    SensitiveErrorCode(String code, String defaultMessage) {
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
