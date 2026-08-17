package io.github.leylaragg.letool.sms.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 短信模块稳定错误码。
 */
public enum SmsErrorCode implements ErrorCode {

    /** 短信配置不合法。 */
    CONFIGURATION_INVALID("SMS_CONFIG_INVALID", "短信配置不合法：{0}"),

    /** 短信请求不合法。 */
    REQUEST_INVALID("SMS_REQUEST_INVALID", "短信请求不合法：{0}"),

    /** 短信发送尝试超过限制。 */
    RATE_LIMITED("SMS_RATE_LIMITED", "短信发送频率超限：{0}"),

    /** 短信厂商拒绝请求。 */
    PROVIDER_REJECTED("SMS_PROVIDER_REJECTED", "短信厂商 {0} 拒绝请求：{1}"),

    /** 短信 SDK 调用失败。 */
    SEND_FAILED("SMS_SEND_FAILED", "通过 {0} 发送短信失败");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建短信错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    SmsErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
