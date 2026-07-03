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
 * <p>重点覆盖 mail starter 的默认轻量化、显式启用和业务项目自定义 Bean 退让行为。</p>
 */
class MailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认配置下只绑定属性，不主动创建邮件基础设施 Bean。
     */
    @Test
    void shouldOnlyBindPropertiesByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MailSender.class);
            assertThat(context).doesNotHaveBean(MailTemplate.class);
            assertThat(context).hasSingleBean(MailProperties.class);
            assertThat(context.getBean(MailProperties.class).isEnabled()).isFalse();
        });
    }

    /**
     * 验证显式开启邮件模块时会注册邮件发送器、邮件模板和配置属性 Bean。
     */
    @Test
    void shouldCreateMailBeansWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MailSender.class);
                    assertThat(context).hasSingleBean(MailTemplate.class);
                    assertThat(context).hasSingleBean(MailProperties.class);
                });
    }

    /**
     * 验证显式关闭邮件模块时不会创建邮件基础设施 Bean。
     */
    @Test
    void shouldNotCreateMailBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MailSender.class);
                    assertThat(context).doesNotHaveBean(MailTemplate.class);
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesMailInfrastructureBeans() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=true")
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
