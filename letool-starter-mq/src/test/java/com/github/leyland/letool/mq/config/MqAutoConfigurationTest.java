package com.github.leyland.letool.mq.config;

import com.github.leyland.letool.mq.core.Message;
import com.github.leyland.letool.mq.core.MessageListener;
import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import com.github.leyland.letool.mq.provider.InMemoryMqProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MqAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖当前 starter 只内置内存队列 provider 的边界，避免 RabbitMQ/Kafka/RocketMQ
 * 配置被误认为已经接入真实 broker。</p>
 */
class MqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MqAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认配置与当前真实内置能力一致，直接注册内存队列 provider。
     */
    @Test
    void shouldUseInMemoryProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MqProperties.class);
            assertThat(context.getBean(MqProperties.class).getDefaultType()).isEqualTo("memory");
            assertThat(context).hasSingleBean(MqProvider.class);
            assertThat(context.getBean(MqProvider.class)).isInstanceOf(InMemoryMqProvider.class);
            assertThat(context).hasSingleBean(MqTemplate.class);
        });
    }

    /**
     * 验证关闭 MQ 模块后不会注册运行时消息队列组件。
     */
    @Test
    void shouldNotCreateRuntimeBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.mq.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MqProperties.class);
                    assertThat(context).doesNotHaveBean(MqProvider.class);
                    assertThat(context).doesNotHaveBean(MqTemplate.class);
                });
    }

    /**
     * 验证 MQ 属性绑定覆盖所有预留 provider 和通用生产/消费配置。
     */
    @Test
    void shouldBindMqProperties() {
        contextRunner
                .withPropertyValues(
                        "letool.mq.default-type=memory",
                        "letool.mq.rabbitmq.host=10.0.0.8",
                        "letool.mq.rabbitmq.port=5673",
                        "letool.mq.rabbitmq.username=admin",
                        "letool.mq.rabbitmq.password=secret",
                        "letool.mq.rabbitmq.virtual-host=/letool",
                        "letool.mq.rocketmq.name-server=10.0.0.9:9876",
                        "letool.mq.rocketmq.group=rocket-group",
                        "letool.mq.rocketmq.topic=orders",
                        "letool.mq.kafka.bootstrap-servers=10.0.0.10:9092",
                        "letool.mq.kafka.group-id=kafka-group",
                        "letool.mq.consumer.concurrency=4",
                        "letool.mq.consumer.max-attempts=6",
                        "letool.mq.consumer.backoff-initial=2500",
                        "letool.mq.producer.retry-times=5",
                        "letool.mq.producer.send-timeout=8000")
                .run(context -> {
                    MqProperties properties = context.getBean(MqProperties.class);

                    assertThat(properties.getRabbitMQ().getHost()).isEqualTo("10.0.0.8");
                    assertThat(properties.getRabbitMQ().getPort()).isEqualTo(5673);
                    assertThat(properties.getRabbitMQ().getUsername()).isEqualTo("admin");
                    assertThat(properties.getRabbitMQ().getPassword()).isEqualTo("secret");
                    assertThat(properties.getRabbitMQ().getVirtualHost()).isEqualTo("/letool");
                    assertThat(properties.getRocketMQ().getNameServer()).isEqualTo("10.0.0.9:9876");
                    assertThat(properties.getRocketMQ().getGroup()).isEqualTo("rocket-group");
                    assertThat(properties.getRocketMQ().getTopic()).isEqualTo("orders");
                    assertThat(properties.getKafka().getBootstrapServers()).isEqualTo("10.0.0.10:9092");
                    assertThat(properties.getKafka().getGroupId()).isEqualTo("kafka-group");
                    assertThat(properties.getConsumer().getConcurrency()).isEqualTo(4);
                    assertThat(properties.getConsumer().getMaxAttempts()).isEqualTo(6);
                    assertThat(properties.getConsumer().getBackoffInitial()).isEqualTo(2500L);
                    assertThat(properties.getProducer().getRetryTimes()).isEqualTo(5);
                    assertThat(properties.getProducer().getSendTimeout()).isEqualTo(8000L);
                });
    }

    /**
     * 验证未内置真实 provider 的类型会 fail-fast，而不是静默回退到内存队列。
     */
    @Test
    void shouldFailFastWhenConfiguredTypeIsNotImplemented() {
        contextRunner
                .withPropertyValues("letool.mq.default-type=kafka")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("kafka")
                            .hasMessageContaining("未内置真实 MqProvider")
                            .hasMessageContaining("自定义 MqProvider");
                });
    }

    /**
     * 验证业务项目提供自定义 provider 时，自动配置不会覆盖它。
     */
    @Test
    void shouldBackOffWhenUserProvidesMqProvider() {
        contextRunner
                .withUserConfiguration(UserMqConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MqProvider.class);
                    assertThat(context.getBean(MqProvider.class))
                            .isSameAs(context.getBean("mqProvider"));
                    assertThat(context).hasSingleBean(MqTemplate.class);
                });
    }

    /**
     * 验证用户自行提供真实 provider 时，预留类型不会触发内置 fail-fast。
     */
    @Test
    void shouldAllowReservedTypeWhenUserProvidesMqProvider() {
        contextRunner
                .withUserConfiguration(UserMqConfiguration.class)
                .withPropertyValues("letool.mq.default-type=rabbitmq")
                .run(context -> {
                    assertThat(context).hasSingleBean(MqProvider.class);
                    assertThat(context.getBean(MqProvider.class)).isInstanceOf(TestMqProvider.class);
                    assertThat(context).hasSingleBean(MqTemplate.class);
                });
    }

    /**
     * 模拟业务项目自行接入 RabbitMQ/Kafka/RocketMQ 等真实 provider 的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMqConfiguration {

        @Bean
        MqProvider mqProvider() {
            return new TestMqProvider();
        }
    }

    /**
     * 用于自动装配测试的消息 provider，不访问任何真实消息中间件。
     */
    static class TestMqProvider implements MqProvider {

        @Override
        public void send(String topic, Message message) {
            // no-op
        }

        @Override
        public void send(String topic, String tag, Message message) {
            // no-op
        }

        @Override
        public void subscribe(String topic, MessageListener listener) {
            // no-op
        }

        @Override
        public void unsubscribe(String topic, MessageListener listener) {
            // no-op
        }
    }
}
