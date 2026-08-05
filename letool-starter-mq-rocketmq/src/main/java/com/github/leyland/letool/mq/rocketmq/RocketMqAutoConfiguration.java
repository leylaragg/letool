package com.github.leyland.letool.mq.rocketmq;

import com.github.leyland.letool.mq.config.MqAutoConfiguration;
import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.provider.StreamOperationsMqProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ Binder Provider 自动配置。
 *
 * <p>该配置只把 Spring Cloud Alibaba RocketMQ Binder 接入 Letool 统一发送门面，
 * RocketMQ 顺序、事务、重试和消费模式等能力继续使用 Binder 原生配置。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(before = MqAutoConfiguration.class)
@ConditionalOnClass(name = "com.alibaba.cloud.stream.binder.rocketmq.RocketMQMessageChannelBinder")
@ConditionalOnProperty(prefix = "letool.mq", name = "enabled", havingValue = "true")
public class RocketMqAutoConfiguration {

    /**
     * 创建 RocketMQ 发送 Provider。
     *
     * @param streamOperations Spring Cloud Stream 发送门面
     * @param bindingServiceProperties Spring Cloud Stream Binding 配置
     * @return RocketMQ 发送 Provider
     */
    @Bean("rocketMqProvider")
    @ConditionalOnMissingBean(name = "rocketMqProvider")
    public MqProvider rocketMqProvider(
            StreamOperations streamOperations,
            BindingServiceProperties bindingServiceProperties) {
        return new StreamOperationsMqProvider(
                "rocketmq",
                "rocketmq",
                streamOperations,
                bindingServiceProperties);
    }
}
