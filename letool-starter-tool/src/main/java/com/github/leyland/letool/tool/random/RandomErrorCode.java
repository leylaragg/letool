package com.github.leyland.letool.tool.random;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 安全随机工具对外暴露的稳定错误码。
 */
public enum RandomErrorCode implements ErrorCode {

    /** 随机数上下界不符合方法契约。 */
    INVALID_RANGE("TOOL_RANDOM_001", "随机数范围无效：{0}"),

    /** 随机字符串或验证码长度不符合方法契约。 */
    INVALID_LENGTH("TOOL_RANDOM_002", "随机字符串长度无效：{0}"),

    /** 自定义随机字符表为空。 */
    INVALID_ALPHABET("TOOL_RANDOM_003", "随机字符表无效：{0}");

    /** 稳定错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建随机工具错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    RandomErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取安全默认消息。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
