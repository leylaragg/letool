package com.github.leyland.letool.mq.rocketmq;

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
 * {@link RocketMqAutoConfiguration} 自动配置组合测试。
 */
@DisplayName("RocketMQ Binder Provider 自动配置")
class RocketMqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RocketMqAutoConfiguration.class,
                    MqAutoConfiguration.class))
            .withBean(BindingServiceProperties.class, () -> mock(BindingServiceProperties.class))
            .withBean(StreamOperations.class, () -> mock(StreamOperations.class));

    /**
     * 验证 MQ 模块关闭时不创建 RocketMQ Provider。
     */
    @Test
    @DisplayName("模块关闭时不应创建 RocketMQ Provider")
    void shouldBeDisabledWithCoreModule() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("rocketMqProvider");
            assertThat(context).doesNotHaveBean(MqTemplate.class);
        });
    }

    /**
     * 验证启用后能够与核心自动配置组合成完整门面。
     */
    @Test
    @DisplayName("启用时应创建 RocketMQ Provider 和 MqTemplate")
    void shouldCreateProviderAndTemplateWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.mq.enabled=true",
                        "letool.mq.default-provider=rocketmq")
                .run(context -> {
                    assertThat(context).hasBean("rocketMqProvider");
                    assertThat(context).hasSingleBean(MqTemplate.class);
                    assertThat(context.getBean("rocketMqProvider", MqProvider.class).name())
                            .isEqualTo("rocketmq");
                });
    }

    /**
     * 验证用户同名 Provider 可以替换默认适配器。
     */
    @Test
    @DisplayName("用户同名 RocketMQ Provider 应使自动配置退让")
    void shouldBackOffForNamedUserProvider() {
        MqProvider customProvider = new TestProvider("rocketmq");

        contextRunner
                .withPropertyValues("letool.mq.enabled=true")
                .withBean("rocketMqProvider", MqProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context.getBean("rocketMqProvider")).isSameAs(customProvider);
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
