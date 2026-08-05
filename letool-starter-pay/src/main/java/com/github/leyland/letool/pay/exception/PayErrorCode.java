package com.github.leyland.letool.pay.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 支付模块稳定错误码。
 *
 * @author leyland
 * @since 2.0.0
 */
public enum PayErrorCode implements ErrorCode {

    /** 支付配置不合法。 */
    CONFIGURATION_INVALID("PAY_001", "支付配置不合法：{0}"),

    /** 支付请求不合法。 */
    REQUEST_INVALID("PAY_002", "支付请求不合法：{0}"),

    /** 未找到支付 Provider。 */
    PROVIDER_NOT_FOUND("PAY_003", "未找到支付 Provider：{0}"),

    /** Provider 名称重复。 */
    DUPLICATE_PROVIDER("PAY_004", "支付 Provider 名称重复：{0}"),

    /** 支付平台拒绝请求。 */
    PROVIDER_REJECTED("PAY_005", "支付平台 {0} 拒绝请求：{1}"),

    /** 支付 SDK 或网络调用失败。 */
    OPERATION_FAILED("PAY_006", "调用支付平台 {0} 失败，当前结果未知"),

    /** 支付通知签名无效。 */
    SIGNATURE_INVALID("PAY_007", "支付平台 {0} 通知验签失败"),

    /** 支付通知格式不合法。 */
    NOTIFICATION_INVALID("PAY_008", "支付平台 {0} 通知不合法：{1}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建支付错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    PayErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码
     */
    @Override
    public String getCode() { return code; }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() { return defaultMessage; }
}
