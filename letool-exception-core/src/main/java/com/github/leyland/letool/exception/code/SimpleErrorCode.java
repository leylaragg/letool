package com.github.leyland.letool.exception.code;

/**
 * 适用于无需单独定义枚举的应用级不可变错误码。
 *
 * @param code 用于日志和响应协议的稳定标识
 * @param defaultMessage 找不到国际化消息时使用的默认消息模板
 */
public record SimpleErrorCode(
        /** 用于日志和响应协议的稳定标识。 */
        String code,
        /** 找不到国际化消息时使用的默认消息模板。 */
        String defaultMessage) implements ErrorCode {

    /**
     * 创建错误码和默认消息模板均有效的对象。
     *
     * @param code 稳定的非空白错误码
     * @param defaultMessage 非空白默认消息模板
     * @throws IllegalArgumentException 当 {@code code} 或 {@code defaultMessage}
     *         为 {@code null} 或空白字符串时抛出
     */
    public SimpleErrorCode {
        requireText(code, "code");
        requireText(defaultMessage, "defaultMessage");
    }

    /**
     * 获取构造时传入的稳定错误码。
     *
     * @return 非空白错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取构造时传入的默认消息模板。
     *
     * @return 非空白默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
