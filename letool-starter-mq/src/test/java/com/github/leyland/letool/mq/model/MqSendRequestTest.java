package com.github.leyland.letool.mq.model;

import com.github.leyland.letool.mq.exception.MqException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MQ 发送请求和结果契约测试。
 */
@DisplayName("MQ 发送请求和结果契约")
class MqSendRequestTest {

    /**
     * 验证请求会清理 Provider 与 Binding 名称。
     */
    @Test
    @DisplayName("应规范化 Provider 与 Binding 名称")
    void shouldNormalizeProviderAndBindingName() {
        MqSendRequest<String> request = new MqSendRequest<>(
                "  Kafka  ",
                "  order-out-0  ",
                new MqMessage<>("payload", null, null));

        assertThat(request.provider()).isEqualTo("kafka");
        assertThat(request.bindingName()).isEqualTo("order-out-0");
    }

    /**
     * 验证空白 Provider 表示使用默认 Provider。
     */
    @Test
    @DisplayName("空白 Provider 应转换为空值")
    void blankProviderShouldMeanDefaultProvider() {
        MqSendRequest<String> request = new MqSendRequest<>(
                " ",
                "order-out-0",
                new MqMessage<>("payload", null, null));

        assertThat(request.provider()).isNull();
    }

    /**
     * 验证 Binding 名称不能为空。
     */
    @Test
    @DisplayName("空白 Binding 名称应拒绝")
    void shouldRejectBlankBindingName() {
        assertThatThrownBy(() -> new MqSendRequest<>(
                null,
                " ",
                new MqMessage<>("payload", null, null)))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_004"));
    }

    /**
     * 验证发送请求必须包含消息。
     */
    @Test
    @DisplayName("消息为空时应拒绝")
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> new MqSendRequest<>(null, "order-out-0", null))
                .isInstanceOf(MqException.class);
    }

    /**
     * 验证发送结果要求完整 Provider、Binding 和时间。
     */
    @Test
    @DisplayName("发送结果应拒绝不完整字段")
    void sendResultShouldRejectIncompleteFields() {
        Instant now = Instant.now();

        assertThat(new MqSendResult("rabbit", "order-out-0", true, now).acceptedAt())
                .isEqualTo(now);
        assertThatThrownBy(() -> new MqSendResult(" ", "order-out-0", true, now))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new MqSendResult("rabbit", " ", true, now))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new MqSendResult("rabbit", "order-out-0", true, null))
                .isInstanceOf(MqException.class);
    }
}
