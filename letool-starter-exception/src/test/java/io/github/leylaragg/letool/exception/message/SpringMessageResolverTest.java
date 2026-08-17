package io.github.leylaragg.letool.exception.message;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.leylaragg.letool.exception.code.CommonErrorCode;
import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SpringMessageResolverTest {

    private static final ErrorCode CODE = ErrorCode.of("TEST_001", "default {0}");

    @AfterEach
    void clearLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void applicationMessageWinsWhenBothSourcesContainTheCode() {
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage(CODE.getCode(), Locale.ENGLISH, "application {0}");
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.ENGLISH, "starter {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);

        String message = resolver.resolve(BusinessException.of(CODE, "value"));

        assertThat(message).isEqualTo("application value");
    }

    @Test
    void starterMessageIsUsedWhenApplicationSourceHasNoMessage() {
        StaticMessageSource application = new StaticMessageSource();
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.SIMPLIFIED_CHINESE, "组件消息 {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(
                        application,
                        starter,
                        Locale.SIMPLIFIED_CHINESE);

        String message = resolver.resolve(BusinessException.of(CODE, "值"));

        assertThat(message).isEqualTo("组件消息 值");
    }

    @Test
    void applicationCodeAsDefaultDoesNotHideAStarterMessage() {
        StaticMessageSource application = new StaticMessageSource();
        application.setUseCodeAsDefaultMessage(true);
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.ENGLISH, "starter {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);

        String message = resolver.resolve(BusinessException.of(CODE, "value"));

        assertThat(message).isEqualTo("starter value");
    }

    @Test
    void malformedApplicationMessageFallsBackToStarterMessage() {
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage(CODE.getCode(), Locale.ENGLISH, "application {0");
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.ENGLISH, "starter {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);

        String message = resolver.resolve(BusinessException.of(CODE, "value"));

        assertThat(message).isEqualTo("starter value");
    }

    @Test
    void malformedStarterMessageFallsBackToCodeDefault() {
        StaticMessageSource application = new StaticMessageSource();
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.ENGLISH, "starter {0");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);

        String message = resolver.resolve(BusinessException.of(CODE, "value"));

        assertThat(message).isEqualTo("default value");
    }

    @Test
    void malformedTemplateWarningContainsOnlySafeLookupContext() {
        ErrorCode logCode = ErrorCode.of("LOG_SAFE_001", "safe default {0}");
        String malformedTemplate = "do-not-log-template {0";
        String secretArgument = "secret-value";
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage(logCode.getCode(), Locale.ENGLISH, malformedTemplate);
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(logCode.getCode(), Locale.ENGLISH, "starter {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);
        Logger logger = (Logger) LoggerFactory.getLogger(SpringMessageResolver.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();

        try {
            logger.addAppender(appender);
            String resolved =
                    resolver.resolve(BusinessException.of(logCode, secretArgument));

            assertThat(resolved).isEqualTo("starter " + secretArgument);
            List<ILoggingEvent> matchingWarnings =
                    appender.list.stream()
                            .filter(event -> event.getFormattedMessage().contains(logCode.getCode()))
                            .toList();
            assertThat(matchingWarnings).hasSize(1);
            ILoggingEvent warning = matchingWarnings.get(0);
            assertThat(warning.getLevel()).isEqualTo(Level.WARN);
            assertThat(warning.getFormattedMessage())
                    .contains("source=application")
                    .contains("code=" + logCode.getCode())
                    .contains("locale=en")
                    .doesNotContain(malformedTemplate, secretArgument);
            assertThat(warning.getMessage()).doesNotContain(malformedTemplate, secretArgument);
            assertThat(Arrays.deepToString(warning.getArgumentArray()))
                    .doesNotContain(malformedTemplate, secretArgument);
            assertThat(warning.toString()).doesNotContain(malformedTemplate, secretArgument);
            assertThat(warning.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            appender.list.clear();
        }
    }

    @Test
    void shippedBundlesResolveFrameworkMessagesInEnglishAndChinese() {
        ResourceBundleMessageSource starter = new ResourceBundleMessageSource();
        starter.setBasename("i18n/letool-exception/messages");
        starter.setDefaultEncoding("UTF-8");
        starter.setFallbackToSystemLocale(false);
        SpringMessageResolver resolver =
                new SpringMessageResolver(null, starter, Locale.ENGLISH);

        assertThat(
                        resolver.resolve(
                                CommonErrorCode.INVALID_ARGUMENT,
                                Locale.ENGLISH,
                                "age"))
                .isEqualTo("Invalid argument: age");
        assertThat(
                        resolver.resolve(
                                CommonErrorCode.INVALID_ARGUMENT,
                                Locale.SIMPLIFIED_CHINESE,
                                "age"))
                .isEqualTo("参数不合法：age");
    }

    @Test
    void codeDefaultIsFormattedWhenBothSourcesHaveNoMessage() {
        SpringMessageResolver resolver =
                new SpringMessageResolver(
                        new StaticMessageSource(),
                        new StaticMessageSource(),
                        Locale.ENGLISH);

        String message = resolver.resolve(BusinessException.of(CODE, "value"));

        assertThat(message).isEqualTo("default value");
    }

    @Test
    void customExceptionMessageBypassesBothSources() {
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage(CODE.getCode(), Locale.ENGLISH, "application {0}");
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.ENGLISH, "starter {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.ENGLISH);

        String message =
                resolver.resolve(BusinessException.custom(CODE, "exact custom text"));

        assertThat(message).isEqualTo("exact custom text");
    }

    @Test
    void concurrentLocaleContextsResolveIndependentlyWithoutLeakingToCaller()
            throws Exception {
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage(CODE.getCode(), Locale.ENGLISH, "application {0}");
        StaticMessageSource starter = new StaticMessageSource();
        starter.addMessage(CODE.getCode(), Locale.SIMPLIFIED_CHINESE, "组件消息 {0}");
        SpringMessageResolver resolver =
                new SpringMessageResolver(application, starter, Locale.GERMAN);
        BusinessException exception = BusinessException.of(CODE, "value");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothBound = new CountDownLatch(2);

        try {
            Future<String> english =
                    executor.submit(
                            () ->
                                    resolveWithLocale(
                                            resolver,
                                            exception,
                                            Locale.ENGLISH,
                                            bothBound));
            Future<String> chinese =
                    executor.submit(
                            () ->
                                    resolveWithLocale(
                                            resolver,
                                            exception,
                                            Locale.SIMPLIFIED_CHINESE,
                                            bothBound));

            assertThat(english.get(5, TimeUnit.SECONDS)).isEqualTo("application value");
            assertThat(chinese.get(5, TimeUnit.SECONDS)).isEqualTo("组件消息 value");
            assertThat(LocaleContextHolder.getLocaleContext()).isNull();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void constructorAndResolveMethodsRequireMandatoryCollaboratorsAndInputs() {
        StaticMessageSource starter = new StaticMessageSource();
        SpringMessageResolver resolver =
                new SpringMessageResolver(null, starter, Locale.ENGLISH);

        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpringMessageResolver(
                                        null,
                                        null,
                                        Locale.ENGLISH))
                .withMessageContaining("starterMessageSource");
        assertThatNullPointerException()
                .isThrownBy(() -> new SpringMessageResolver(null, starter, null))
                .withMessageContaining("defaultLocale");
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null))
                .withMessageContaining("exception");
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null, Locale.ENGLISH, "value"))
                .withMessageContaining("errorCode");
    }

    private static String resolveWithLocale(
            SpringMessageResolver resolver,
            BusinessException exception,
            Locale locale,
            CountDownLatch bothBound)
            throws InterruptedException {
        LocaleContextHolder.setLocale(locale);
        bothBound.countDown();
        try {
            if (!bothBound.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("resolver tasks did not start concurrently");
            }
            return resolver.resolve(exception);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
