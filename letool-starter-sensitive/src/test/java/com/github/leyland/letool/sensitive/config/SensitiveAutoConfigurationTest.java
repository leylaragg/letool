package com.github.leyland.letool.sensitive.config;

import com.github.leyland.letool.sensitive.core.SensitiveProcessor;
import com.github.leyland.letool.sensitive.core.SensitiveStrategyRegistry;
import com.github.leyland.letool.sensitive.core.SensitiveType;
import com.github.leyland.letool.sensitive.jackson.SensitiveModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏自动配置的关键装配测试。
 */
@DisplayName("脱敏自动配置")
class SensitiveAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SensitiveAutoConfiguration.class));

    /**
     * 验证默认环境注册可直接使用的核心组件与 Jackson 模块。
     */
    @Test
    @DisplayName("默认应装配生产化脱敏组件")
    void shouldCreateDefaultSensitiveBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SensitiveProperties.class);
            assertThat(context).hasSingleBean(SensitiveStrategyRegistry.class);
            assertThat(context).hasSingleBean(SensitiveProcessor.class);
            assertThat(context).hasSingleBean(SensitiveModule.class);
        });
    }

    /**
     * 验证模块总开关和 Jackson 子开关都能真实控制 Bean 装配。
     */
    @Test
    @DisplayName("配置开关应控制实际组件")
    void shouldHonorConfigurationSwitches() {
        contextRunner.withPropertyValues("letool.sensitive.jackson.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveProcessor.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                });

        contextRunner.withPropertyValues("letool.sensitive.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SensitiveProperties.class);
                    assertThat(context).doesNotHaveBean(SensitiveStrategyRegistry.class);
                    assertThat(context).doesNotHaveBean(SensitiveProcessor.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                });
    }

    /**
     * 验证未引入 Jackson 的应用仍可使用编程式脱敏组件。
     */
    @Test
    @DisplayName("缺少 Jackson 时应保留核心脱敏组件")
    void shouldKeepCoreBeansWithoutJackson() {
        contextRunner.withClassLoader(new FilteredClassLoader("com.fasterxml.jackson.databind"))
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveStrategyRegistry.class);
                    assertThat(context).hasSingleBean(SensitiveProcessor.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                });
    }

    /**
     * 验证用户提供的策略注册表优先于 Starter 默认实现。
     */
    @Test
    @DisplayName("用户策略注册表应覆盖默认实现")
    void shouldBackOffForUserStrategyRegistry() {
        contextRunner.withUserConfiguration(UserRegistryConfiguration.class)
                .run(context -> {
                    SensitiveStrategyRegistry registry = context.getBean(SensitiveStrategyRegistry.class);
                    SensitiveProcessor processor = context.getBean(SensitiveProcessor.class);

                    assertThat(registry).isSameAs(context.getBean("userSensitiveStrategyRegistry"));
                    assertThat(processor.mask("13812345678", SensitiveType.PHONE)).isEqualTo("用户策略");
                });
    }

    /**
     * 测试使用的用户扩展配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserRegistryConfiguration {

        /**
         * 创建覆盖手机号规则的用户策略注册表。
         *
         * @return 用户自定义策略注册表
         */
        @Bean
        SensitiveStrategyRegistry userSensitiveStrategyRegistry() {
            return SensitiveStrategyRegistry.builder()
                    .register(SensitiveType.PHONE, (value, context) -> "用户策略")
                    .build();
        }
    }
}
