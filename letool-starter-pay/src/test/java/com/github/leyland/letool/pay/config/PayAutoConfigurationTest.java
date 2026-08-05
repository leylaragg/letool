package com.github.leyland.letool.pay.config;

import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.core.PayTemplate;
import com.github.leyland.letool.pay.provider.MockPayProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PayAutoConfiguration} 自动配置契约测试。
 */
class PayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PayAutoConfiguration.class));

    /**
     * 验证支付模块默认保持关闭。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PayProvider.class);
            assertThat(context).doesNotHaveBean(PayTemplate.class);
        });
    }

    /**
     * 验证启用模块但未注册 Provider 时快速失败。
     */
    @Test
    void shouldFailFastWithoutProvider() {
        contextRunner.withPropertyValues("letool.pay.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("PayProvider");
                });
    }

    /**
     * 验证 Mock Provider 只能通过独立开关显式启用。
     */
    @Test
    void shouldCreateExplicitMockProvider() {
        contextRunner.withPropertyValues(
                        "letool.pay.enabled=true",
                        "letool.pay.mock.enabled=true",
                        "letool.pay.default-provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(PayProvider.class);
                    assertThat(context.getBean(PayProvider.class)).isInstanceOf(MockPayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                });
    }

    /**
     * 验证用户提供模板时自动配置会完整退让。
     */
    @Test
    void shouldBackOffForUserTemplate() {
        contextRunner.withPropertyValues("letool.pay.enabled=true")
                .withUserConfiguration(UserPayConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                    assertThat(context.getBean(PayTemplate.class)).isSameAs(context.getBean("customPayTemplate"));
                });
    }

    /**
     * 用户接管支付模板的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserPayConfiguration {

        /** @return 测试 Mock Provider */
        @Bean
        PayProvider userPayProvider() { return new MockPayProvider(); }

        /**
         * 创建用户自定义支付模板。
         *
         * @param provider 测试 Provider
         * @param properties 支付属性
         * @return 自定义支付模板
         */
        @Bean
        PayTemplate customPayTemplate(PayProvider provider, PayProperties properties) {
            return new PayTemplate(java.util.List.of(provider), properties);
        }
    }
}
