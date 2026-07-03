package com.github.leyland.letool.mq.config;

import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import com.github.leyland.letool.mq.exception.MqException;
import com.github.leyland.letool.mq.provider.InMemoryMqProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQ 模块自动配置 —— 注册 {@link MqProvider} 和 {@link MqTemplate}.
 *
 * <p>当前版本仅内置 {@link InMemoryMqProvider}。当 {@code letool.mq.default-type}
 * 配置为 {@code rabbitmq}、{@code rocketmq} 或 {@code kafka} 时，用户需要自行提供
 * {@link MqProvider} Bean；内置配置不会静默回退到内存队列。</p>
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
 * # 预留给真实 RabbitMQ provider 的配置；必须同时注册自定义 MqProvider Bean
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
     * <p>当前只内置 {@link InMemoryMqProvider}。非 {@code memory} 类型会在没有用户自定义
     * {@link MqProvider} Bean 时 fail-fast，避免把开发内存队列误当成真实外部 broker。</p>
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

        throw unsupportedProvider(type);
    }

    /**
     * 构建未内置 provider 的配置错误。
     *
     * @param type 配置的 MQ 类型
     * @return MQ 配置异常
     */
    private MqException unsupportedProvider(String type) {
        return new MqException("[letool-mq] " + type + " 未内置真实 MqProvider；"
                + "当前 starter 只内置 memory。请改用 letool.mq.default-type=memory，"
                + "或在业务项目中注册自定义 MqProvider Bean。");
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
