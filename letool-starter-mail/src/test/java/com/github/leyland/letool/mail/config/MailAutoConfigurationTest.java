package com.github.leyland.letool.mail.config;

import com.github.leyland.letool.mail.core.MailSender;
import com.github.leyland.letool.mail.core.MailTemplate;
import com.github.leyland.letool.mail.model.MailRequest;
import com.github.leyland.letool.mail.model.MailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MailAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义邮件发送器和邮件模板时，mail starter 是否正确退让。</p>
 */
class MailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证用户提供邮件发送器和邮件模板时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesMailInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserMailConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MailSender.class);
                    assertThat(context).hasSingleBean(MailTemplate.class);
                    assertThat(context.getBean(MailSender.class))
                            .isSameAs(context.getBean("mailSender"));
                    assertThat(context.getBean(MailTemplate.class))
                            .isSameAs(context.getBean("mailTemplate"));
                });
    }

    /**
     * 模拟业务项目自行接管 mail 基础设施的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMailConfiguration {

        @Bean
        MailSender mailSender() {
            return new TestMailSender();
        }

        @Bean
        MailTemplate mailTemplate(MailSender mailSender) {
            return new MailTemplate(mailSender, 1);
        }
    }

    /**
     * 用于自动装配测试的邮件发送器，不访问真实 SMTP 服务。
     */
    static class TestMailSender implements MailSender {

        @Override
        public MailResponse send(MailRequest request) {
            return MailResponse.success("test-message");
        }
    }
}
