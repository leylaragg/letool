package io.github.leylaragg.letool.exception.config;

import io.github.leylaragg.letool.exception.code.CommonErrorCode;
import io.github.leylaragg.letool.exception.code.SimpleErrorCode;
import io.github.leylaragg.letool.exception.core.SystemException;
import io.github.leylaragg.letool.exception.message.DefaultMessageResolver;
import io.github.leylaragg.letool.exception.message.MessageBundleContributor;
import io.github.leylaragg.letool.exception.message.MessageResolver;
import io.github.leylaragg.letool.exception.message.SpringMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionAutoConfigurationTest {

    private static final String CONTRIBUTOR_BASENAME =
            "i18n/test-exception-contributor/messages";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ExceptionAutoConfiguration.class));

    @Test
    void defaultContextShouldCreateSpringMessageResolver() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MessageResolver.class);
                    assertThat(context.getBean(MessageResolver.class))
                            .isExactlyInstanceOf(SpringMessageResolver.class);
                });
    }

    @Test
    void disabledExceptionModuleShouldNotCreateResolver() {
        contextRunner
                .withPropertyValues("letool.exception.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(MessageResolver.class);
                        });
    }

    @Test
    void disabledI18nShouldUseDefaultMessageResolver() {
        contextRunner
                .withPropertyValues("letool.exception.i18n.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MessageResolver.class);
                            assertThat(context.getBean(MessageResolver.class))
                                    .isExactlyInstanceOf(DefaultMessageResolver.class);
                        });
    }

    @Test
    void userMessageResolverShouldWin() {
        contextRunner
                .withUserConfiguration(UserResolverConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MessageResolver.class);
                            assertThat(context.getBean(MessageResolver.class))
                                    .isSameAs(context.getBean("userMessageResolver"));
                        });
    }

    @Test
    void applicationMessageSourceShouldBeRetainedAndOverrideStarterText() {
        contextRunner
                .withUserConfiguration(ApplicationMessageSourceConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MessageResolver.class);
                            assertThat(
                                            context.getBean(
                                                    AbstractApplicationContext
                                                            .MESSAGE_SOURCE_BEAN_NAME))
                                    .isSameAs(context.getBean(StaticMessageSource.class));

                            String message =
                                    context.getBean(MessageResolver.class)
                                            .resolve(
                                                    CommonErrorCode.SYSTEM_ERROR,
                                                    Locale.ENGLISH);
                            assertThat(message).isEqualTo("Application system error");
                        });
    }

    @Test
    void contributorBasenamesShouldBeTrimmedDeduplicatedInFirstSeenOrder() {
        List<String> basenames =
                ExceptionAutoConfiguration.normalizeBasenames(
                        List.of(
                                MessageBundleContributor.of(" first ", "shared"),
                                MessageBundleContributor.of("second", "shared")));

        assertThat(basenames).containsExactly("first", "shared", "second");
    }

    @Test
    void contributorBasenamesShouldBeCollectedForResourceLookup() {
        contextRunner
                .withUserConfiguration(ContributorConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MessageResolver.class);

                            String message =
                                    context.getBean(MessageResolver.class)
                                            .resolve(
                                                    new SimpleErrorCode(
                                                            "TEST_CONTRIBUTOR",
                                                            "contributor fallback"),
                                                    Locale.ENGLISH);
                            assertThat(message).isEqualTo("Contributed test message");
                        });
    }

    @Test
    void boundDefaultLocaleShouldApplyWithoutLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
        try {
            contextRunner
                    .withPropertyValues(
                            "letool.exception.i18n.default-locale=en_US")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(LocaleContextHolder.getLocaleContext()).isNull();

                                String message =
                                        context.getBean(MessageResolver.class)
                                                .resolve(
                                                        SystemException.of(
                                                                CommonErrorCode.SYSTEM_ERROR));
                                assertThat(message).isEqualTo("Internal system error");
                            });
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserResolverConfiguration {

        @Bean
        MessageResolver userMessageResolver() {
            return new DefaultMessageResolver(Locale.ENGLISH);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationMessageSourceConfiguration {

        @Bean
        MessageSource messageSource() {
            StaticMessageSource messageSource = new StaticMessageSource();
            messageSource.addMessage(
                    CommonErrorCode.SYSTEM_ERROR.getCode(),
                    Locale.ENGLISH,
                    "Application system error");
            return messageSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ContributorConfiguration {

        @Bean
        MessageBundleContributor paddedAndDuplicateContributor() {
            return MessageBundleContributor.of(
                    " " + CONTRIBUTOR_BASENAME + " ",
                    CONTRIBUTOR_BASENAME);
        }

        @Bean
        MessageBundleContributor duplicateContributor() {
            return MessageBundleContributor.of(CONTRIBUTOR_BASENAME);
        }
    }
}
