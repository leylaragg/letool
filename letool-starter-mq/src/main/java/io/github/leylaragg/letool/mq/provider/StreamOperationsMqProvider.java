package io.github.leylaragg.letool.mq.provider;

import io.github.leylaragg.letool.mq.core.MqProvider;
import io.github.leylaragg.letool.mq.exception.MqErrorCode;
import io.github.leylaragg.letool.mq.exception.MqException;
import io.github.leylaragg.letool.mq.model.MqMessage;
import io.github.leylaragg.letool.mq.model.MqSendRequest;
import io.github.leylaragg.letool.mq.model.MqSendResult;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

import java.time.Instant;
import java.util.Locale;

/**
 * 基于 Spring Cloud Stream {@link StreamOperations} 的 MQ Provider。
 *
 * <p>该类只负责消息信封转换、Binder 选择和发送结果归一化。Broker 连接、序列化、确认、
 * 重试、死信和事务等行为由 Spring Cloud Stream 及具体 Binder 管理。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class StreamOperationsMqProvider implements MqProvider {

    private final String name;
    private final String binderType;
    private final StreamOperations streamOperations;
    private final BindingServiceProperties bindingServiceProperties;

    /**
     * 创建 Spring Cloud Stream MQ Provider。
     *
     * @param name Letool Provider 名称
     * @param binderType Spring Cloud Stream Binder 类型
     * @param streamOperations Spring Cloud Stream 发送契约
     * @param bindingServiceProperties Spring Cloud Stream Binding 配置
     */
    public StreamOperationsMqProvider(
            String name,
            String binderType,
            StreamOperations streamOperations,
            BindingServiceProperties bindingServiceProperties) {
        this.name = normalizeRequired(name, "Provider 名称");
        this.binderType = normalizeRequired(binderType, "Binder 类型");
        if (streamOperations == null) {
            throw MqException.of(MqErrorCode.CONFIGURATION_INVALID,
                    "StreamOperations 不能为空");
        }
        if (bindingServiceProperties == null) {
            throw MqException.of(MqErrorCode.CONFIGURATION_INVALID,
                    "BindingServiceProperties 不能为空");
        }
        this.streamOperations = streamOperations;
        this.bindingServiceProperties = bindingServiceProperties;
    }

    /**
     * 返回 Provider 名称。
     *
     * @return 规范化 Provider 名称
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * 将 Letool 消息转换为 Spring Messaging 消息并交给指定 Binder。
     *
     * @param request 已校验的发送请求
     * @return 发送通道接受结果
     */
    @Override
    public MqSendResult send(MqSendRequest<?> request) {
        if (request == null) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "request 不能为空");
        }
        Message<?> springMessage = toSpringMessage(request.message());
        String binderName = resolveBinderName(request.bindingName());
        try {
            boolean accepted = streamOperations.send(
                    request.bindingName(),
                    binderName,
                    springMessage);
            if (!accepted) {
                throw MqException.of(
                        MqErrorCode.SEND_REJECTED,
                        name,
                        request.bindingName());
            }
            return new MqSendResult(
                    name,
                    request.bindingName(),
                    true,
                    Instant.now());
        } catch (MqException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw MqException.causedBy(MqErrorCode.SEND_FAILED, exception, name);
        }
    }

    /**
     * 解析 Binding 最终使用的 Binder 配置名称。
     *
     * <p>Binding 未显式选择 Binder 时依次使用 Spring Cloud Stream 全局默认 Binder 和
     * Provider 对应的默认类型名；显式配置时保留用户的 Binder 别名，并校验别名指向的
     * 真实类型与当前 Provider 一致。</p>
     *
     * @param bindingName Spring Cloud Stream Binding 名称
     * @return 可传给 {@link StreamOperations} 的 Binder 配置名称
     */
    private String resolveBinderName(String bindingName) {
        String configuredBinder = bindingServiceProperties.getBinder(bindingName);
        if (configuredBinder == null || configuredBinder.isBlank()) {
            configuredBinder = bindingServiceProperties.getDefaultBinder();
        }
        if (configuredBinder == null || configuredBinder.isBlank()) {
            return binderType;
        }
        String binderName = configuredBinder.trim();
        BinderProperties binderProperties = bindingServiceProperties.getBinders().get(binderName);
        String configuredType = binderProperties == null || binderProperties.getType() == null
                || binderProperties.getType().isBlank()
                ? binderName
                : binderProperties.getType().trim();
        if (!binderType.equals(configuredType.toLowerCase(Locale.ROOT))) {
            throw MqException.of(
                    MqErrorCode.CONFIGURATION_INVALID,
                    "Binding " + bindingName + " 的 Binder " + binderName
                            + " 类型与 Provider " + name + " 不一致");
        }
        return binderName;
    }

    /**
     * 构建 Spring Messaging 消息。
     *
     * @param message Letool 不可变消息信封
     * @return Spring Messaging 消息
     */
    private Message<?> toSpringMessage(MqMessage<?> message) {
        MessageBuilder<?> builder = MessageBuilder.withPayload(message.payload())
                .copyHeaders(message.headers());
        if (message.contentType() != null) {
            builder.setHeader(
                    MessageHeaders.CONTENT_TYPE,
                    parseContentType(message.contentType()));
        }
        return builder.build();
    }

    /**
     * 解析用户声明的 MIME 类型。
     *
     * @param contentType MIME 类型文本
     * @return Spring MIME 类型
     */
    private MimeType parseContentType(String contentType) {
        try {
            return MimeType.valueOf(contentType);
        } catch (IllegalArgumentException exception) {
            throw MqException.causedBy(
                    MqErrorCode.MESSAGE_INVALID,
                    exception,
                    "contentType 格式不合法");
        }
    }

    /**
     * 规范化必填名称。
     *
     * @param value 原始名称
     * @param fieldName 字段说明
     * @return 小写名称
     */
    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw MqException.of(MqErrorCode.CONFIGURATION_INVALID,
                    fieldName + "不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
