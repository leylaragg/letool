package com.github.leyland.letool.mq.model;

import com.github.leyland.letool.mq.exception.MqErrorCode;
import com.github.leyland.letool.mq.exception.MqException;

import java.util.Locale;

/**
 * 不可变 MQ 发送请求。
 *
 * @param provider 可选 Provider 名称；为空时使用默认 Provider
 * @param bindingName 非空 Spring Cloud Stream 输出 Binding 名称
 * @param message 非空消息信封
 * @param <T> 消息正文类型
 * @author leyland
 * @since 2.0.0
 */
public record MqSendRequest<T>(String provider, String bindingName, MqMessage<T> message) {

    /**
     * 校验请求并规范化路由名称。
     *
     * @param provider 可选 Provider 名称；为空时使用默认 Provider
     * @param bindingName 非空 Spring Cloud Stream 输出 Binding 名称
     * @param message 非空消息信封
     */
    public MqSendRequest {
        provider = normalizeProvider(provider);
        if (bindingName == null || bindingName.isBlank()) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "bindingName 不能为空");
        }
        bindingName = bindingName.trim();
        if (message == null) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "message 不能为空");
        }
    }

    /**
     * 规范化可选 Provider 名称。
     *
     * @param value 原始 Provider 名称
     * @return 小写 Provider 名称；空白值返回 {@code null}
     */
    private static String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
