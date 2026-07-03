package com.github.leyland.letool.sms.config;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.core.SmsTemplate;
import com.github.leyland.letool.sms.model.SmsResult;
import com.github.leyland.letool.sms.provider.AliyunSmsProvider;
import com.github.leyland.letool.sms.provider.MockSmsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义短信提供者和短信模板时，sms starter 是否正确退让。</p>
 */
class SmsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmsAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证短信模块默认保持未启用状态，不因引入 starter 就创建模拟发送 provider。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SmsProvider.class);
            assertThat(context).doesNotHaveBean(SmsTemplate.class);
        });
    }

    /**
     * 验证启用短信模块但未显式允许 mock/stub provider 时会 fail-fast。
     */
    @Test
    void shouldFailFastWhenEnabledWithoutMockModeOrCustomProvider() {
        contextRunner
                .withPropertyValues("letool.sms.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("未启用 mock")
                            .hasMessageContaining("自定义 SmsProvider");
                });
    }

    /**
     * 验证显式开启 mock 模式后才会创建 MockSmsProvider。
     */
    @Test
    void shouldCreateMockProviderWhenMockModeIsExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.sms.enabled=true",
                        "letool.sms.mock-enabled=true",
                        "letool.sms.default-provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context.getBean(SmsProvider.class)).isInstanceOf(MockSmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                });
    }

    /**
     * 验证阿里云模拟 provider 也必须显式开启 mock 模式。
     */
    @Test
    void shouldCreateAliyunStubProviderWhenMockModeIsExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.sms.enabled=true",
                        "letool.sms.mock-enabled=true",
                        "letool.sms.default-provider=aliyun")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context.getBean(SmsProvider.class)).isInstanceOf(AliyunSmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                });
    }

    /**
     * 验证未知短信 provider 不会静默回退到 MockSmsProvider。
     */
    @Test
    void shouldFailFastWhenProviderIsUnsupported() {
        contextRunner
                .withPropertyValues(
                        "letool.sms.enabled=true",
                        "letool.sms.mock-enabled=true",
                        "letool.sms.default-provider=huawei")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("huawei")
                            .hasMessageContaining("不支持的短信 provider");
                });
    }

    /**
     * 验证用户提供短信提供者和短信模板时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesSmsInfrastructureBeans() {
        contextRunner
                .withPropertyValues("letool.sms.enabled=true")
                .withUserConfiguration(UserSmsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsProvider.class);
                    assertThat(context).hasSingleBean(SmsTemplate.class);
                    assertThat(context.getBean(SmsProvider.class))
                            .isSameAs(context.getBean("smsProvider"));
                    assertThat(context.getBean(SmsTemplate.class))
                            .isSameAs(context.getBean("smsTemplate"));
                });
    }

    /**
     * 模拟业务项目自行接管 sms 基础设施的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserSmsConfiguration {

        @Bean
        SmsProvider smsProvider() {
            return new TestSmsProvider();
        }

        @Bean
        SmsTemplate smsTemplate(SmsProvider smsProvider, SmsProperties properties) {
            return new SmsTemplate(smsProvider, properties);
        }
    }

    /**
     * 用于自动装配测试的短信提供者，不访问真实短信服务商 API。
     */
    static class TestSmsProvider implements SmsProvider {

        @Override
        public SmsResult send(String phone, String templateCode, Map<String, String> params) {
            return SmsResult.success("test-request");
        }

        @Override
        public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
            return SmsResult.success("test-batch-request");
        }

        @Override
        public String getProviderName() {
            return "test";
        }
    }
}
