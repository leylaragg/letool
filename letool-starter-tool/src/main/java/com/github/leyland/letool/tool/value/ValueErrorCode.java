package com.github.leyland.letool.tool.value;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 基础值、集合和枚举工具对外暴露的稳定错误码。
 */
public enum ValueErrorCode implements ErrorCode {

    /** 调用参数不符合方法契约。 */
    INVALID_ARGUMENT("TOOL_VALUE_001", "基础值参数无效：{0}"),

    /** 枚举属性读取失败。 */
    ENUM_ACCESS_FAILED("TOOL_VALUE_002", "枚举属性读取失败：{0}"),

    /** 严格查询未找到对应枚举常量。 */
    ENUM_CONSTANT_NOT_FOUND("TOOL_VALUE_003", "未找到枚举常量：{0}"),

    /** 枚举描述映射存在重复键。 */
    DUPLICATE_ENUM_LABEL("TOOL_VALUE_004", "枚举描述存在重复值：{0}");

    /** 稳定的机器可读错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建基础值错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    ValueErrorCode(String code, String defaultMessage) {
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
