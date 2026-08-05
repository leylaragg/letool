package com.github.leyland.letool.mq.model;

import com.github.leyland.letool.mq.exception.MqErrorCode;
import com.github.leyland.letool.mq.exception.MqException;

import java.time.Instant;
import java.util.Locale;

/**
 * 不可变 MQ 发送结果。
 *
 * <p>{@code accepted=true} 仅表示消息被 Spring Cloud Stream 发送通道接受，不表示 Broker 已持久化，
 * 也不表示消费者已经成功处理。</p>
 *
 * @param provider 实际执行发送的 Provider 名称
 * @param bindingName 实际发送的 Binding 名称
 * @param accepted 发送通道是否接受消息
 * @param acceptedAt 发送通道接受消息的时间
 * @author leyland
 * @since 2.0.0
 */
public record MqSendResult(
        String provider,
        String bindingName,
        boolean accepted,
        Instant acceptedAt) {

    /**
     * 校验并规范化发送结果。
     *
     * @param provider 实际执行发送的 Provider 名称
     * @param bindingName 实际发送的 Binding 名称
     * @param accepted 发送通道是否接受消息
     * @param acceptedAt 发送通道接受消息的时间
     */
    public MqSendResult {
        if (provider == null || provider.isBlank()) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "结果 provider 不能为空");
        }
        provider = provider.trim().toLowerCase(Locale.ROOT);
        if (bindingName == null || bindingName.isBlank()) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "结果 bindingName 不能为空");
        }
        bindingName = bindingName.trim();
        if (acceptedAt == null) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "结果 acceptedAt 不能为空");
        }
    }
}
