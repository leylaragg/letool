package com.github.leyland.letool.mq.rabbit;

import com.github.leyland.letool.mq.config.MqAutoConfiguration;
import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import com.github.leyland.letool.mq.model.MqSendRequest;
import com.github.leyland.letool.mq.model.MqSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link RabbitMqAutoConfiguration} 自动配置组合测试。
 */
@DisplayName("RabbitMQ Binder Provider 自动配置")
class RabbitMqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RabbitMqAutoConfiguration.class,
                    MqAutoConfiguration.class))
            .withBean(BindingServiceProperties.class, () -> mock(BindingServiceProperties.class))
            .withBean(StreamOperations.class, () -> mock(StreamOperations.class));

    /**
     * 验证 MQ 模块关闭时不创建 Rabbit Provider。
     */
    @Test
    @DisplayName("模块关闭时不应创建 Rabbit Provider")
    void shouldBeDisabledWithCoreModule() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("rabbitMqProvider");
            assertThat(context).doesNotHaveBean(MqTemplate.class);
        });
    }

    /**
     * 验证启用后能够与核心自动配置组合成完整门面。
     */
    @Test
    @DisplayName("启用时应创建 Rabbit Provider 和 MqTemplate")
    void shouldCreateProviderAndTemplateWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.mq.enabled=true",
                        "letool.mq.default-provider=rabbit")
                .run(context -> {
                    assertThat(context).hasBean("rabbitMqProvider");
                    assertThat(context).hasSingleBean(MqTemplate.class);
                    assertThat(context.getBean("rabbitMqProvider", MqProvider.class).name())
                            .isEqualTo("rabbit");
                });
    }

    /**
     * 验证用户同名 Provider 可以替换默认适配器。
     */
    @Test
    @DisplayName("用户同名 Rabbit Provider 应使自动配置退让")
    void shouldBackOffForNamedUserProvider() {
        MqProvider customProvider = new TestProvider("rabbit");

        contextRunner
                .withPropertyValues("letool.mq.enabled=true")
                .withBean("rabbitMqProvider", MqProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context.getBean("rabbitMqProvider")).isSameAs(customProvider);
                    assertThat(context).hasSingleBean(MqTemplate.class);
                });
    }

    /**
     * 用户替换场景使用的测试 Provider。
     */
    private record TestProvider(String name) implements MqProvider {

        /**
         * 返回固定接受结果。
         *
         * @param request 发送请求
         * @return 接受结果
         */
        @Override
        public MqSendResult send(MqSendRequest<?> request) {
            return new MqSendResult(name, request.bindingName(), true, Instant.now());
        }
    }
}
