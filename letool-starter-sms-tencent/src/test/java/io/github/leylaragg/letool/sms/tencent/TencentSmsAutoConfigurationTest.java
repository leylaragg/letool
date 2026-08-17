package io.github.leylaragg.letool.sms.tencent;

import io.github.leylaragg.letool.sms.config.SmsAutoConfiguration;
import io.github.leylaragg.letool.sms.core.SmsProvider;
import io.github.leylaragg.letool.sms.core.SmsTemplate;
import io.github.leylaragg.letool.sms.provider.MockSmsProvider;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link TencentSmsAutoConfiguration} 自动配置测试。
 */
class TencentSmsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TencentSmsAutoConfiguration.class,
                    SmsAutoConfiguration.class))
            .withPropertyValues(
                    "letool.sms.enabled=true",
                    "letool.sms.rate-limit.enabled=false",
                    "letool.sms.tencent.sdk-app-id=1400000000",
                    "letool.sms.tencent.sign-name=测试签名");

    /**
     * 验证用户提供官方客户端后自动创建 Provider 和短信模板。
     */
    @Test
    void shouldUseUserClientAndCreateProvider() {
        contextRunner
                .withUserConfiguration(UserClientConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsClient.class);
                    assertThat(context).hasSingleBean(TencentSmsProvider.class);
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                    assertThat(context.getBean(SmsClient.class)).isSameAs(context.getBean("tencentClient"));
                });
    }

    /**
     * 验证关闭腾讯云 Provider 后不会创建厂商 Bean。
     */
    @Test
    void shouldStayInactiveWhenProviderDisabled() {
        contextRunner
                .withPropertyValues("letool.sms.tencent.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("SmsProvider");
                });
    }

    /**
     * 验证用户接管腾讯云 Provider 时不会要求静态密钥或创建官方客户端。
     */
    @Test
    void shouldBackOffClientWhenUserProvidesNamedProvider() {
        contextRunner
                .withUserConfiguration(UserProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SmsClient.class);
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                });
    }

    /**
     * 用户接管官方客户端的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserClientConfiguration {

        /**
         * 创建 Mock 腾讯云客户端。
         *
         * @return Mock 客户端
         */
        @Bean
        SmsClient tencentClient() {
            return mock(SmsClient.class);
        }
    }

    /**
     * 用户接管腾讯云 Provider 的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserProviderConfiguration {

        /**
         * 创建用户自定义 Provider。
         *
         * @return 自定义 Provider
         */
        @Bean
        SmsProvider tencentSmsProvider() {
            return new MockSmsProvider();
        }
    }
}
