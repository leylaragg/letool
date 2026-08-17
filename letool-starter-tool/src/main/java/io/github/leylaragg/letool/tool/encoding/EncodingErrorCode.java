package io.github.leylaragg.letool.tool.encoding;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 二进制文本编解码工具对外暴露的稳定错误码。
 */
public enum EncodingErrorCode implements ErrorCode {

    /** 必填编码参数为空或不符合方法契约。 */
    INVALID_ARGUMENT("TOOL_ENCODING_001", "编码参数无效：{0}"),

    /** 标准或 URL 安全 Base64 文本无法解码。 */
    BASE64_DECODE_FAILED("TOOL_ENCODING_002", "Base64 解码失败：{0}"),

    /** 十六进制文本长度或字符内容不合法。 */
    HEX_DECODE_FAILED("TOOL_ENCODING_003", "十六进制解码失败");

    /** 稳定错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建编码错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    EncodingErrorCode(String code, String defaultMessage) {
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
