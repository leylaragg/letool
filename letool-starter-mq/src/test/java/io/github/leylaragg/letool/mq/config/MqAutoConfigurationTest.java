package io.github.leylaragg.letool.mq.config;

import io.github.leylaragg.letool.mq.core.MqProvider;
import io.github.leylaragg.letool.mq.core.MqTemplate;
import io.github.leylaragg.letool.mq.model.MqSendRequest;
import io.github.leylaragg.letool.mq.model.MqSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MqAutoConfiguration} 自动配置契约测试。
 */
@DisplayName("MQ 核心自动配置契约")
class MqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MqAutoConfiguration.class));

    /**
     * 验证核心模块默认关闭，不制造内存 Provider。
     */
    @Test
    @DisplayName("默认关闭时不应创建 MQ Bean")
    void shouldBeDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MqTemplate.class);
            assertThat(context).doesNotHaveBean(MqProvider.class);
        });
    }

    /**
     * 验证显式启用且存在 Provider 时创建发送门面。
     */
    @Test
    @DisplayName("启用且存在 Provider 时应创建 MqTemplate")
    void shouldCreateTemplateWhenEnabledWithProvider() {
        contextRunner
                .withPropertyValues("letool.mq.enabled=true")
                .withBean("rabbitMqProvider", MqProvider.class, () -> new TestProvider("rabbit"))
                .run(context -> assertThat(context).hasSingleBean(MqTemplate.class));
    }

    /**
     * 验证启用但没有 Provider 时应用启动失败。
     */
    @Test
    @DisplayName("启用但没有 Provider 时应快速失败")
    void shouldFailFastWhenEnabledWithoutProvider() {
        contextRunner
                .withPropertyValues("letool.mq.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(io.github.leylaragg.letool.mq.exception.MqException.class)
                            .hasMessageContaining("MQ_001");
                });
    }

    /**
     * 验证用户提供的发送门面优先于自动配置。
     */
    @Test
    @DisplayName("用户 MqTemplate 应使自动配置退让")
    void shouldBackOffForUserTemplate() {
        TestProvider provider = new TestProvider("custom");
        MqTemplate userTemplate = new MqTemplate(List.of(provider), null);

        contextRunner
                .withPropertyValues("letool.mq.enabled=true")
                .withBean(MqTemplate.class, () -> userTemplate)
                .run(context -> assertThat(context.getBean(MqTemplate.class)).isSameAs(userTemplate));
    }

    /**
     * 验证禁用状态不会因用户 Provider 而自动开启。
     */
    @Test
    @DisplayName("禁用时用户 Provider 不应触发 MqTemplate")
    void disabledModuleShouldIgnoreAvailableProvider() {
        contextRunner
                .withBean("rabbitMqProvider", MqProvider.class, () -> new TestProvider("rabbit"))
                .run(context -> assertThat(context).doesNotHaveBean(MqTemplate.class));
    }

    /**
     * 验证配置属性能够选择多 Provider 默认项。
     */
    @Test
    @DisplayName("default-provider 应选择多 Provider 默认项")
    void defaultProviderPropertyShouldSelectProvider() {
        contextRunner
                .withPropertyValues(
                        "letool.mq.enabled=true",
                        "letool.mq.default-provider=kafka")
                .withBean("rabbitMqProvider", MqProvider.class, () -> new TestProvider("rabbit"))
                .withBean("kafkaMqProvider", MqProvider.class, () -> new TestProvider("kafka"))
                .run(context -> {
                    MqSendResult result = context.getBean(MqTemplate.class)
                            .send("order-out-0", "payload");
                    assertThat(result.provider()).isEqualTo("kafka");
                });
    }

    /**
     * 自动配置测试使用的最小 Provider。
     */
    private static final class TestProvider implements MqProvider {

        private final String name;

        /**
         * 创建测试 Provider。
         *
         * @param name Provider 名称
         */
        private TestProvider(String name) {
            this.name = name;
        }

        /**
         * 返回 Provider 名称。
         *
         * @return Provider 名称
         */
        @Override
        public String name() {
            return name;
        }

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
