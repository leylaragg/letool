package com.github.leyland.letool.tool.id;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * ID 生成工具对外暴露的稳定错误码。
 */
public enum IdErrorCode implements ErrorCode {

    /** NanoId 等 ID 参数不符合方法契约。 */
    INVALID_ARGUMENT("TOOL_ID_001", "ID 参数无效：{0}"),

    /** Snowflake 工作节点或数据中心配置不合法。 */
    NODE_CONFIGURATION_FAILED("TOOL_ID_002", "Snowflake 节点配置无效：{0}"),

    /** 系统时钟回拨超过配置的容忍范围。 */
    CLOCK_ROLLBACK("TOOL_ID_003", "Snowflake 时钟回拨超过容忍范围"),

    /** 当前时间无法放入 Snowflake 的 41 位时间部分。 */
    TIMESTAMP_OUT_OF_RANGE("TOOL_ID_004", "Snowflake 时间戳超出可用范围"),

    /** Snowflake 等待下一毫秒时被线程中断。 */
    GENERATION_INTERRUPTED("TOOL_ID_005", "Snowflake ID 生成被中断");

    /** 稳定错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建 ID 生成错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    IdErrorCode(String code, String defaultMessage) {
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
