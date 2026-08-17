package io.github.leylaragg.letool.pay.wechat;

import io.github.leylaragg.letool.pay.config.PayAutoConfiguration;
import io.github.leylaragg.letool.pay.core.PayProvider;
import io.github.leylaragg.letool.pay.core.PayTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link WechatPayAutoConfiguration} 自动配置契约测试。
 */
class WechatPayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WechatPayAutoConfiguration.class,
                    PayAutoConfiguration.class));

    /**
     * 验证微信支付 Provider 默认不会启用。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(WechatPaySdk.class);
            assertThat(context).doesNotHaveBean(WechatPayProvider.class);
        });
    }

    /**
     * 验证缺失商户密钥配置时会快速失败。
     */
    @Test
    void shouldFailFastForMissingCredentials() {
        contextRunner.withPropertyValues("letool.pay.wechat.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("app-id");
                });
    }

    /**
     * 验证完整配置下会复用 SDK 边界并注册 Provider。
     */
    @Test
    void shouldCreateWechatProvider() {
        contextRunner.withUserConfiguration(UserSdkConfiguration.class)
                .withPropertyValues(
                        "letool.pay.wechat.enabled=true",
                        "letool.pay.enabled=true",
                        "letool.pay.default-provider=wechat",
                        "letool.pay.wechat.app-id=wx-app",
                        "letool.pay.wechat.mch-id=merchant",
                        "letool.pay.wechat.notify-url=https://example.com/notify")
                .run(context -> {
                    assertThat(context).hasSingleBean(WechatPaySdk.class);
                    assertThat(context).hasSingleBean(WechatPayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                    assertThat(context.getBean(PayProvider.class).getProviderName()).isEqualTo("wechat");
                });
    }

    /**
     * 用户提供 SDK 调用边界的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserSdkConfiguration {

        /** @return 微信支付 SDK 调用边界 Mock */
        @Bean
        WechatPaySdk wechatPaySdk() { return mock(WechatPaySdk.class); }
    }
}
