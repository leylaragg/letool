package com.github.leyland.letool.tool.spel;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * SpEL 表达式工具对外暴露的稳定错误码。
 */
public enum SpelErrorCode implements ErrorCode {

    /** 表达式语法不合法，无法完成解析。 */
    PARSE_FAILED("TOOL_SPEL_001", "SpEL 表达式解析失败"),

    /** 表达式在指定上下文中无法完成求值或类型转换。 */
    EVALUATION_FAILED("TOOL_SPEL_002", "SpEL 表达式求值失败");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息。 */
    private final String defaultMessage;

    /**
     * 创建 SpEL 错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息
     */
    SpelErrorCode(String code, String defaultMessage) {
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
     * 获取默认错误消息。
     *
     * @return 非空默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
