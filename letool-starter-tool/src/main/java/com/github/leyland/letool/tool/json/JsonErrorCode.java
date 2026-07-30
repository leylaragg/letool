package com.github.leyland.letool.tool.json;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 工具模块 JSON 边界的稳定错误码。
 */
public enum JsonErrorCode implements ErrorCode {

    /** Java 对象无法序列化为 JSON。 */
    SERIALIZATION_FAILED("TOOL_JSON_001", "JSON 序列化失败"),

    /** JSON 输入无法反序列化为指定 Java 类型。 */
    DESERIALIZATION_FAILED("TOOL_JSON_002", "JSON 反序列化失败，目标类型：{0}");

    private final String code;
    private final String defaultMessage;

    /**
     * 定义稳定的 JSON 错误码。
     *
     * @param code 机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    JsonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 返回稳定的机器可读错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 返回默认消息模板。
     *
     * @return 非空默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
