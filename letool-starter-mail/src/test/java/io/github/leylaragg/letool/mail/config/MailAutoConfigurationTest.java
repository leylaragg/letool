package io.github.leylaragg.letool.mail.config;

import io.github.leylaragg.letool.mail.core.MailSender;
import io.github.leylaragg.letool.mail.core.MailTemplate;
import io.github.leylaragg.letool.mail.exception.MailException;
import io.github.leylaragg.letool.mail.model.MailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MailAutoConfiguration} 启用、校验和用户扩展退让测试。
 */
@DisplayName("MailAutoConfiguration 自动配置测试")
class MailAutoConfigurationTest {

    /** 基础自动配置运行器。 */
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MailAutoConfiguration.class)
                    )
                    .withPropertyValues(
                            "spring.main.allow-bean-definition-overriding=false"
                    );

    @Test
    @DisplayName("默认只绑定属性而不创建发送基础设施")
    void shouldOnlyBindPropertiesByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MailSender.class);
            assertThat(context).doesNotHaveBean(MailTemplate.class);
            assertThat(context).hasSingleBean(MailProperties.class);
            assertThat(context.getBean(MailProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    @DisplayName("显式启用且账户有效时应创建邮件基础设施")
    void shouldCreateMailBeansWithValidAccount() {
        enabledRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MailSender.class);
            assertThat(context).hasSingleBean(MailTemplate.class);
            assertThat(context).hasSingleBean(MailProperties.class);
        });
    }

    @Test
    @DisplayName("显式启用但默认账户缺失时应启动失败")
    void shouldFailFastWhenDefaultAccountIsMissing() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = rootCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOfSatisfying(
                                    MailException.class,
                                    exception -> assertThat(exception.getCode())
                                            .isEqualTo("MAIL_001")
                            );
                });
    }

    @Test
    @DisplayName("异步线程数不合法时应以配置错误启动失败")
    void shouldFailFastWhenAsyncPoolSizeIsInvalid() {
        enabledRunner()
                .withPropertyValues("letool.mail.async-pool-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = rootCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOfSatisfying(
                                    MailException.class,
                                    exception -> {
                                        assertThat(exception.getCode())
                                                .isEqualTo("MAIL_001");
                                        assertThat(exception.getMessage())
                                                .contains("async-pool-size");
                                    }
                            );
                });
    }

    @Test
    @DisplayName("异步队列容量不合法时应以配置错误启动失败")
    void shouldFailFastWhenAsyncQueueCapacityIsInvalid() {
        enabledRunner()
                .withPropertyValues("letool.mail.async-queue-capacity=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = rootCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOfSatisfying(
                                    MailException.class,
                                    exception -> {
                                        assertThat(exception.getCode())
                                                .isEqualTo("MAIL_001");
                                        assertThat(exception.getMessage())
                                                .contains("async-queue-capacity");
                                    }
                            );
                });
    }

    @Test
    @DisplayName("显式关闭时不应创建邮件基础设施")
    void shouldNotCreateMailBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MailSender.class);
                    assertThat(context).doesNotHaveBean(MailTemplate.class);
                });
    }

    @Test
    @DisplayName("用户只提供 MailSender 时不应强制要求 SMTP 账户")
    void shouldUseCustomSenderWithoutSmtpAccount() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=true")
                .withUserConfiguration(UserMailSenderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MailSender.class);
                    assertThat(context).hasSingleBean(MailTemplate.class);
                    assertThat(context.getBean(MailSender.class))
                            .isSameAs(context.getBean("mailSender"));
                });
    }

    @Test
    @DisplayName("用户同时提供发送器和门面时自动配置应完全退让")
    void shouldBackOffForCompleteUserInfrastructure() {
        contextRunner
                .withPropertyValues("letool.mail.enabled=true")
                .withUserConfiguration(UserMailInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MailSender.class);
                    assertThat(context).hasSingleBean(MailTemplate.class);
                    assertThat(context.getBean(MailSender.class))
                            .isSameAs(context.getBean("mailSender"));
                    assertThat(context.getBean(MailTemplate.class))
                            .isSameAs(context.getBean("mailTemplate"));
                });
    }

    /**
     * 创建显式启用且包含有效默认账户的运行器。
     *
     * @return 邮件模块运行器
     */
    private ApplicationContextRunner enabledRunner() {
        return contextRunner.withPropertyValues(
                "letool.mail.enabled=true",
                "letool.mail.default-account=primary",
                "letool.mail.accounts.primary.host=smtp.example.com",
                "letool.mail.accounts.primary.port=587",
                "letool.mail.accounts.primary.username=mailer@example.com",
                "letool.mail.accounts.primary.password=secret",
                "letool.mail.accounts.primary.protocol=smtp",
                "letool.mail.accounts.primary.auth=true",
                "letool.mail.accounts.primary.starttls=true",
                "letool.mail.accounts.primary.from=mailer@example.com"
        );
    }

    /**
     * 获取异常链最底层原因。
     *
     * @param throwable 异常链入口
     * @return 最底层异常
     */
    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 只提供用户自定义发送器的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMailSenderConfiguration {

        /**
         * 创建不依赖 SMTP 账户的自定义发送器。
         *
         * @return 自定义发送器
         */
        @Bean
        MailSender mailSender() {
            return request -> MailResponse.success("custom-message");
        }
    }

    /**
     * 同时提供用户自定义发送器和门面的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMailInfrastructureConfiguration {

        /**
         * 创建自定义发送器。
         *
         * @return 自定义发送器
         */
        @Bean
        MailSender mailSender() {
            return request -> MailResponse.success("custom-message");
        }

        /**
         * 创建自定义邮件门面。
         *
         * @param mailSender 自定义发送器
         * @return 自定义门面
         */
        @Bean
        MailTemplate mailTemplate(MailSender mailSender) {
            return new MailTemplate(mailSender, 1);
        }
    }
}
