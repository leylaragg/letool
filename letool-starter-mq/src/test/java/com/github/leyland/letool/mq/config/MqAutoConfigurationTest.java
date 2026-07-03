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
