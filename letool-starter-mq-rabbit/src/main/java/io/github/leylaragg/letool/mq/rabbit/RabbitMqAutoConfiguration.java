package io.github.leylaragg.letool.mq.rabbit;

import io.github.leylaragg.letool.mq.config.MqAutoConfiguration;
import io.github.leylaragg.letool.mq.core.MqProvider;
import io.github.leylaragg.letool.mq.provider.StreamOperationsMqProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.context.annotation.Bean;

/**
 * RabbitMQ Binder Provider 自动配置。
 *
 * <p>该配置只把 Spring Cloud Stream Rabbit Binder 接入 Letool 统一发送门面，
 * RabbitMQ 连接、重试、死信和确认等能力继续使用 Binder 原生配置。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(before = MqAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder")
@ConditionalOnProperty(prefix = "letool.mq", name = "enabled", havingValue = "true")
public class RabbitMqAutoConfiguration {

    /**
     * 创建 RabbitMQ 发送 Provider。
     *
     * @param streamOperations Spring Cloud Stream 发送门面
     * @param bindingServiceProperties Spring Cloud Stream Binding 配置
     * @return RabbitMQ 发送 Provider
     */
    @Bean("rabbitMqProvider")
    @ConditionalOnMissingBean(name = "rabbitMqProvider")
    public MqProvider rabbitMqProvider(
            StreamOperations streamOperations,
            BindingServiceProperties bindingServiceProperties) {
        return new StreamOperationsMqProvider(
                "rabbit",
                "rabbit",
                streamOperations,
                bindingServiceProperties);
    }
}
