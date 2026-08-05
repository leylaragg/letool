package com.github.leyland.letool.mq.config;

import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQ 核心便利门面自动配置。
 *
 * <p>自动配置只收集已经存在的 {@link MqProvider} 并创建统一发送门面，不创建内存队列，
 * 也不复制 Spring Cloud Stream 的 Binder 和 Binding 配置。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MqProperties.class)
@ConditionalOnProperty(prefix = "letool.mq", name = "enabled", havingValue = "true")
public class MqAutoConfiguration {

    /**
     * 创建统一 MQ 发送门面。
     *
     * @param providers 容器中的全部 MQ Provider
     * @param properties MQ 路由配置
     * @return 统一 MQ 发送门面
     */
    @Bean
    @ConditionalOnMissingBean(MqTemplate.class)
    public MqTemplate mqTemplate(
            ObjectProvider<MqProvider> providers,
            MqProperties properties) {
        return new MqTemplate(
                providers.orderedStream().toList(),
                properties.getDefaultProvider());
    }
}
