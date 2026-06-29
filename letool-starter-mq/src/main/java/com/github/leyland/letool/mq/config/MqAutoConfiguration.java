package com.github.leyland.letool.mq.config;

import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import com.github.leyland.letool.mq.provider.InMemoryMqProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQ 模块自动配置 —— 根据 {@code letool.mq.default-type} 注册对应的 {@link MqProvider} 和 {@link MqTemplate}.
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link MqProvider} —— 消息队列提供者（默认 {@link InMemoryMqProvider}，无需外部 MQ 依赖）</li>
 *   <li>{@link MqTemplate} —— 统一消息发送/订阅入口</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * # 使用内存队列（开发/测试环境）
 * letool.mq.default-type=memory
 *
 * # 使用 RabbitMQ
 * letool.mq.default-type=rabbitmq
 * letool.mq.rabbitmq.host=192.168.1.100
 * letool.mq.rabbitmq.port=5672
 * }</pre>
 *
 * <h3>扩展自定义 Provider</h3>
 * <p>实现 {@link MqProvider} 接口并注册为 Spring Bean 即可自动替换默认实现：</p>
 * <pre>{@code
 * @Component
 * public class MyCustomMqProvider implements MqProvider {
 *     // 实现 send / subscribe / unsubscribe
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MqProperties.class)
public class MqAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqAutoConfiguration.class);

    // ======================== MqProvider Bean ========================

    /**
     * 注册 MQ 消息提供者 —— 根据配置创建对应实现.
     *
     * <p>当前默认使用 {@link InMemoryMqProvider} 内存队列实现，
     * 后续版本将根据 {@code defaultType} 配置自动装配 RabbitMQ / RocketMQ / Kafka 实现.</p>
     *
     * @param properties MQ 配置属性
     * @return MqProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MqProvider mqProvider(MqProperties properties) {
        // 根据 defaultType 选择对应的 Provider 实现
        String type = properties.getDefaultType();
        log.info("[letool-mq] 初始化 MQ Provider，类型: {}", type);

        if ("memory".equalsIgnoreCase(type)) {
            return new InMemoryMqProvider();
        }

        // RabbitMQ / RocketMQ / Kafka 的具体实现由对应子模块提供
        // 当前所有非 memory 类型均回退到 InMemoryMqProvider，确保开箱即用
        // TODO: 当引入 rabbitmq-starter / rocketmq-starter / kafka-starter 时，
        //       由各子模块通过 @ConditionalOnProperty 注册对应的 MqProvider Bean
        log.warn("[letool-mq] 未找到 {} 对应的 MqProvider 实现，将回退到 InMemoryMqProvider（内存队列）", type);
        return new InMemoryMqProvider();
    }

    // ======================== MqTemplate Bean ========================

    /**
     * 注册 MQ 操作模板 —— 提供统一的消息发送/订阅 API.
     *
     * @param mqProvider 消息队列提供者
     * @param properties MQ 配置属性
     * @return MqTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MqTemplate mqTemplate(MqProvider mqProvider, MqProperties properties) {
        log.info("[letool-mq] 初始化 MqTemplate");
        return new MqTemplate(mqProvider, properties);
    }
}
