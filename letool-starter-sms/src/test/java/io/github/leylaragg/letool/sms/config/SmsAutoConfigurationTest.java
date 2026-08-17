package io.github.leylaragg.letool.sms.config;

import io.github.leylaragg.letool.sms.core.SmsProvider;
import io.github.leylaragg.letool.sms.core.SmsRateLimiter;
import io.github.leylaragg.letool.sms.core.SmsTemplate;
import io.github.leylaragg.letool.sms.model.SmsRecipientResult;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import io.github.leylaragg.letool.sms.provider.MockSmsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsAutoConfiguration} 自动配置契约测试。
 */
class SmsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmsAutoConfiguration.class));

    /**
     * 验证模块默认不创建任何短信基础设施。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SmsProvider.class);
            assertThat(context).doesNotHaveBean(SmsTemplate.class);
        });
    }

    /**
     * 验证启用模块但没有 Provider 时会快速失败。
     */
    @Test
    void shouldFailFastWhenEnabledWithoutProvider() {
        contextRunner
                .withPropertyValues("letool.sms.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("SmsProvider");
                });
    }

    /**
     * 验证只有显式启用时才创建 Mock Provider。
     */
    @Test
    void shouldCreateMockProviderWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.sms.enabled=true",
                        "letool.sms.mock.enabled=true",
                        "letool.sms.rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context.getBean(SmsProvider.class)).isInstanceOf(MockSmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                });
    }

    /**
     * 验证用户提供完整短信基础设施时自动配置会退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesSmsInfrastructure() {
        contextRunner
                .withPropertyValues("letool.sms.enabled=true")
                .withUserConfiguration(UserSmsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context).hasSingleBean(SmsRateLimiter.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                    assertThat(context.getBean(SmsRateLimiter.class))
                            .isSameAs(context.getBean("customSmsRateLimiter"));
                });
    }

    /**
     * 用户接管短信基础设施的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserSmsConfiguration {

        /**
         * 创建测试 Provider。
         *
         * @return 测试 Provider
         */
        @Bean
        SmsProvider smsProvider() {
            return new TestSmsProvider();
        }

        /**
         * 创建测试限流器。
         *
         * @return 无操作限流器
         */
        @Bean
        SmsRateLimiter customSmsRateLimiter() {
            return SmsRateLimiter.noOp();
        }

        /**
         * 创建用户自定义短信模板。
         *
         * @param smsProvider 短信 Provider
         * @param properties 短信配置
         * @param rateLimiter 限流器
         * @return 自定义短信模板
         */
        @Bean
        SmsTemplate smsTemplate(
                SmsProvider smsProvider,
                SmsProperties properties,
                SmsRateLimiter rateLimiter) {
            return new SmsTemplate(List.of(smsProvider), properties, rateLimiter);
        }
    }

    /**
     * 自动配置测试使用的短信 Provider。
     */
    private static final class TestSmsProvider implements SmsProvider {

        /**
         * 返回固定成功结果。
         *
         * @param request 短信请求
         * @return 成功结果
         */
        @Override
        public SmsResult send(SmsRequest request) {
            List<SmsRecipientResult> recipients = request.getPhones().stream()
                    .map(phone -> SmsRecipientResult.success(phone, "OK", "成功"))
                    .toList();
            return SmsResult.fromRecipients("test", "request", "OK", "成功", recipients);
        }

        /**
         * 获取 Provider 名称。
         *
         * @return 固定返回 {@code test}
         */
        @Override
        public String getProviderName() {
            return "test";
        }
    }
}
