package com.github.leyland.letool.pay.alipay;

import com.alipay.api.AlipayClient;
import com.github.leyland.letool.pay.config.PayAutoConfiguration;
import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.core.PayTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link AlipayPayAutoConfiguration} 自动配置契约测试。
 */
class AlipayPayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AlipayPayAutoConfiguration.class,
                    PayAutoConfiguration.class));

    /**
     * 验证支付宝 Provider 默认不会启用。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AlipayClient.class);
            assertThat(context).doesNotHaveBean(AlipayPayProvider.class);
        });
    }

    /**
     * 验证缺失密钥配置时启动会快速失败。
     */
    @Test
    void shouldFailFastForMissingCredentials() {
        contextRunner.withPropertyValues("letool.pay.alipay.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("app-id");
                });
    }

    /**
     * 验证用户提供官方客户端时自动配置会复用并注册 Provider。
     */
    @Test
    void shouldReuseUserAlipayClient() {
        contextRunner.withUserConfiguration(UserClientConfiguration.class)
                .withPropertyValues(
                        "letool.pay.alipay.enabled=true",
                        "letool.pay.enabled=true",
                        "letool.pay.default-provider=alipay",
                        "letool.pay.alipay.app-id=app-id",
                        "letool.pay.alipay.private-key=private-key",
                        "letool.pay.alipay.alipay-public-key=public-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlipayClient.class);
                    assertThat(context).hasSingleBean(AlipayPayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                    assertThat(context.getBean(PayProvider.class).getProviderName()).isEqualTo("alipay");
                });
    }

    /**
     * 用户提供支付宝客户端的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserClientConfiguration {

        /** @return 官方客户端 Mock */
        @Bean
        AlipayClient alipayClient() { return mock(AlipayClient.class); }
    }
}
