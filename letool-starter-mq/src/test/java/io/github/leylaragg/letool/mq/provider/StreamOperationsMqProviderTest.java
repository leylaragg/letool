package io.github.leylaragg.letool.mq.provider;

import io.github.leylaragg.letool.mq.exception.MqException;
import io.github.leylaragg.letool.mq.model.MqMessage;
import io.github.leylaragg.letool.mq.model.MqSendRequest;
import io.github.leylaragg.letool.mq.model.MqSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StreamOperationsMqProvider} Spring Cloud Stream 发送适配测试。
 */
@DisplayName("Spring Cloud Stream MQ Provider 适配")
class StreamOperationsMqProviderTest {

    private StreamOperations streamOperations;
    private BindingServiceProperties bindingServiceProperties;
    private StreamOperationsMqProvider provider;

    /**
     * 创建隔离外部框架边界的测试对象。
     */
    @BeforeEach
    void setUp() {
        streamOperations = mock(StreamOperations.class);
        bindingServiceProperties = new BindingServiceProperties();
        provider = new StreamOperationsMqProvider(
                " Rabbit ",
                " rabbit ",
                streamOperations,
                bindingServiceProperties);
    }

    /**
     * 验证 Provider 名称会被规范化。
     */
    @Test
    @DisplayName("Provider 名称应规范化")
    void providerNameShouldBeNormalized() {
        assertThat(provider.name()).isEqualTo("rabbit");
    }

    /**
     * 验证 Payload、Header 和 Content-Type 被完整转换。
     */
    @Test
    @DisplayName("应完整映射 Spring Messaging 消息")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldMapSpringMessagingMessage() {
        MqSendRequest<String> request = request(
                new MqMessage<>(
                        "payload",
                        Map.of("traceId", "trace-1"),
                        "application/json"));
        when(streamOperations.send(eq("order-out-0"), eq("rabbit"), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(true);

        MqSendResult result = provider.send(request);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamOperations).send(eq("order-out-0"), eq("rabbit"), messageCaptor.capture());
        Message<?> springMessage = messageCaptor.getValue();
        assertThat(springMessage.getPayload()).isEqualTo("payload");
        assertThat(springMessage.getHeaders().get("traceId")).isEqualTo("trace-1");
        assertThat(springMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE))
                .isEqualTo(MimeType.valueOf("application/json"));
        assertThat(result.provider()).isEqualTo("rabbit");
        assertThat(result.bindingName()).isEqualTo("order-out-0");
        assertThat(result.accepted()).isTrue();
        assertThat(result.acceptedAt()).isNotNull();
    }

    /**
     * 验证没有 Content-Type 时不会写入对应 Header。
     */
    @Test
    @DisplayName("未配置 Content-Type 时不应写入 Header")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldOmitContentTypeWhenNotConfigured() {
        when(streamOperations.send(eq("order-out-0"), eq("rabbit"), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(true);

        provider.send(request(new MqMessage<>("payload", null, null)));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamOperations).send(eq("order-out-0"), eq("rabbit"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getHeaders()).doesNotContainKey(MessageHeaders.CONTENT_TYPE);
    }

    /**
     * 验证发送通道返回 false 时使用稳定拒绝错误码。
     */
    @Test
    @DisplayName("发送通道拒绝消息时应抛出结构化异常")
    void rejectedSendShouldThrowStructuredException() {
        when(streamOperations.send(eq("order-out-0"), eq("rabbit"), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(false);

        assertThatThrownBy(() -> provider.send(request(new MqMessage<>("payload", null, null))))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_006"));
    }

    /**
     * 验证底层框架异常会保留为原因链。
     */
    @Test
    @DisplayName("底层发送异常应保留原因链")
    void frameworkFailureShouldPreserveCause() {
        IllegalStateException cause = new IllegalStateException("binder unavailable");
        when(streamOperations.send(eq("order-out-0"), eq("rabbit"), org.mockito.ArgumentMatchers.<Object>any()))
                .thenThrow(cause);

        assertThatThrownBy(() -> provider.send(request(new MqMessage<>("payload", null, null))))
                .isInstanceOfSatisfying(MqException.class, exception -> {
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_005");
                    assertThat(exception.getCause()).isSameAs(cause);
                    assertThat(exception.getMessage()).doesNotContain("binder unavailable");
                });
    }

    /**
     * 验证非法 MIME 类型不会进入 Binder。
     */
    @Test
    @DisplayName("非法 Content-Type 应转换为消息异常")
    void invalidContentTypeShouldFailBeforeSending() {
        MqSendRequest<String> request = request(
                new MqMessage<>("payload", null, "not-a-mime-type"));

        assertThatThrownBy(() -> provider.send(request))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_004"));
    }

    /**
     * 验证 Provider 构造参数必须完整。
     */
    @Test
    @DisplayName("Provider 构造参数不完整时应拒绝")
    void constructorShouldRejectInvalidArguments() {
        assertThatThrownBy(() -> new StreamOperationsMqProvider(
                " ", "rabbit", streamOperations, bindingServiceProperties))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new StreamOperationsMqProvider(
                "rabbit", " ", streamOperations, bindingServiceProperties))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new StreamOperationsMqProvider(
                "rabbit", "rabbit", null, bindingServiceProperties))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new StreamOperationsMqProvider(
                "rabbit", "rabbit", streamOperations, null))
                .isInstanceOf(MqException.class);
    }

    /**
     * 验证 Binding 显式配置的同类型 Binder 别名优先于默认类型名。
     */
    @Test
    @DisplayName("应使用 Binding 配置的同类型 Binder 别名")
    void shouldUseConfiguredBinderAlias() {
        configureBindingBinder("rabbit-primary", "rabbit");
        when(streamOperations.send(
                eq("order-out-0"),
                eq("rabbit-primary"),
                org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(true);

        provider.send(request(new MqMessage<>("payload", null, null)));

        verify(streamOperations).send(
                eq("order-out-0"),
                eq("rabbit-primary"),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    /**
     * 验证全局默认 Binder 别名在 Binding 未单独配置时生效。
     */
    @Test
    @DisplayName("应使用 Spring Cloud Stream 全局默认 Binder 别名")
    void shouldUseConfiguredDefaultBinderAlias() {
        configureBinderAlias("rabbit-secondary", "rabbit");
        bindingServiceProperties.setDefaultBinder("rabbit-secondary");
        when(streamOperations.send(
                eq("order-out-0"),
                eq("rabbit-secondary"),
                org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(true);

        provider.send(request(new MqMessage<>("payload", null, null)));

        verify(streamOperations).send(
                eq("order-out-0"),
                eq("rabbit-secondary"),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    /**
     * 验证 Binding 绑定到其他中间件时快速失败，避免结果中的 Provider 与实际 Binder 不一致。
     */
    @Test
    @DisplayName("Binding Binder 类型与 Provider 不一致时应拒绝发送")
    void shouldRejectMismatchedBindingBinderType() {
        configureBindingBinder("kafka-primary", "kafka");

        assertThatThrownBy(() -> provider.send(
                request(new MqMessage<>("payload", null, null))))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_001"));
    }

    /**
     * 配置测试 Binding 使用的 Binder 别名及其真实类型。
     *
     * @param binderName Binder 配置名称
     * @param binderType Binder 真实类型
     */
    private void configureBindingBinder(String binderName, String binderType) {
        BindingProperties bindingProperties = new BindingProperties();
        bindingProperties.setBinder(binderName);
        bindingServiceProperties.setBindings(Map.of("order-out-0", bindingProperties));

        configureBinderAlias(binderName, binderType);
    }

    /**
     * 配置测试使用的 Binder 别名及其真实类型。
     *
     * @param binderName Binder 配置名称
     * @param binderType Binder 真实类型
     */
    private void configureBinderAlias(String binderName, String binderType) {
        BinderProperties binderProperties = new BinderProperties();
        binderProperties.setType(binderType);
        bindingServiceProperties.setBinders(Map.of(binderName, binderProperties));
    }

    /**
     * 创建测试发送请求。
     *
     * @param message 测试消息
     * @return 使用 Rabbit Provider 的发送请求
     */
    private MqSendRequest<String> request(MqMessage<String> message) {
        return new MqSendRequest<>("rabbit", "order-out-0", message);
    }
}
