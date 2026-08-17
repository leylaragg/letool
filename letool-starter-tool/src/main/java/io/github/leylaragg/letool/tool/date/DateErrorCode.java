package io.github.leylaragg.letool.tool.date;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 日期时间工具对外暴露的稳定错误码。
 */
public enum DateErrorCode implements ErrorCode {

    /** 必填日期时间参数为空或不符合方法契约。 */
    INVALID_ARGUMENT("TOOL_DATE_001", "日期时间参数无效：{0}"),

    /** 日期时间文本无法按照指定格式严格解析。 */
    PARSE_FAILED("TOOL_DATE_002", "日期时间解析失败"),

    /** 日期时间对象无法按照指定格式输出。 */
    FORMAT_FAILED("TOOL_DATE_003", "日期时间格式化失败"),

    /** 日期时间对象、时区或时间戳之间无法完成转换。 */
    CONVERSION_FAILED("TOOL_DATE_004", "日期时间转换失败");

    /** 稳定错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建日期时间错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    DateErrorCode(String code, String defaultMessage) {
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
