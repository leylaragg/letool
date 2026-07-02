package com.github.leyland.letool.sms.config;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.core.SmsTemplate;
import com.github.leyland.letool.sms.model.SmsResult;
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
     * 验证用户提供短信提供者和短信模板时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesSmsInfrastructureBeans() {
        contextRunner
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
