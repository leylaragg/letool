package com.github.leyland.letool.exception.code;

import java.io.Serializable;

/**
 * 与面向用户的国际化文案相互独立的异常标识。
 *
 * <p>实现类需要提供用于程序判断的稳定非空白错误码，以及可以包含
 * {@link java.text.MessageFormat} 占位符的非空白默认消息。应用可以根据错误码解析
 * HTTP 国际化响应，同时保留默认消息作为可靠的日志兜底。异常对象会持有并序列化错误码，
 * 因此错误码实现类也必须支持 Java 序列化；若实现类包含额外状态，还需保证这些状态可以序列化。</p>
 */
public interface ErrorCode extends Serializable {

    /**
     * 获取用于诊断和外部错误协议的稳定标识。
     *
     * @return 非空白错误码
     */
    String getCode();

    /**
     * 获取找不到国际化消息时使用的默认消息模板。
     *
     * @return 非空白 {@link java.text.MessageFormat} 模板
     */
    String getDefaultMessage();

    /**
     * 创建无需单独定义枚举的不可变错误码。
     *
     * @param code 稳定的非空白错误码
     * @param defaultMessage 非空白默认消息模板
     * @return 不可变错误码对象
     * @throws IllegalArgumentException 当任一参数为 {@code null} 或空白字符串时抛出
     */
    static ErrorCode of(String code, String defaultMessage) {
        return new SimpleErrorCode(code, defaultMessage);
    }
}
