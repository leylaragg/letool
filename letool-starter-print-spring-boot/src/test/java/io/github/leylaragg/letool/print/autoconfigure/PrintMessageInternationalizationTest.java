package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration;
import io.github.leylaragg.letool.exception.message.DefaultMessageResolver;
import io.github.leylaragg.letool.exception.message.MessageResolver;
import io.github.leylaragg.letool.print.exception.PrintErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证打印错误码在异常 Starter 中的资源发现、覆盖和回退行为。 */
class PrintMessageInternationalizationTest {

    private static final String[] MESSAGE_RESOURCES = {
        "i18n/letool-print/messages.properties",
        "i18n/letool-print/messages_zh_CN.properties",
        "i18n/letool-print/messages_en.properties"
    };

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    ExceptionAutoConfiguration.class,
                                    PrintSpelAutoConfiguration.class,
                                    PrintAutoConfiguration.class));

    @Test
    void everyPrintBundleShouldDeclareAllStableErrorCodes() throws IOException {
        Set<String> expectedCodes =
                Arrays.stream(PrintErrorCode.values())
                        .map(PrintErrorCode::getCode)
                        .collect(Collectors.toSet());

        for (String resource : MESSAGE_RESOURCES) {
            assertThat(loadBundle(resource).keySet()).as(resource).containsExactlyInAnyOrderElementsOf(expectedCodes);
        }
    }

    @Test
    void printMessagesShouldResolveInChineseAndEnglish() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    MessageResolver resolver = context.getBean(MessageResolver.class);

                    assertThat(resolver.resolve(PrintErrorCode.INVALID_REQUEST, Locale.SIMPLIFIED_CHINESE, "字段为空"))
                            .isEqualTo("打印请求不合法：字段为空");
                    assertThat(resolver.resolve(PrintErrorCode.INVALID_REQUEST, Locale.ENGLISH, "missing field"))
                            .isEqualTo("Invalid print request: missing field");
                });
    }

    @Test
    void applicationMessageSourceShouldOverridePrintBundle() {
        contextRunner
                .withUserConfiguration(ApplicationMessages.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            String message =
                                    context.getBean(MessageResolver.class)
                                            .resolve(PrintErrorCode.INVALID_REQUEST, Locale.ENGLISH, "detail");
                            assertThat(message).isEqualTo("Application print request: detail");
                        });
    }

    @Test
    void disabledInternationalizationShouldUseErrorCodeDefaultMessage() {
        contextRunner
                .withPropertyValues("letool.exception.i18n.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(MessageResolver.class)).isInstanceOf(DefaultMessageResolver.class);

                            String message =
                                    context.getBean(MessageResolver.class)
                                            .resolve(PrintErrorCode.INVALID_REQUEST, Locale.ENGLISH, "detail");
                            assertThat(message).isEqualTo("打印请求不合法：detail");
                        });
    }

    /** 直接读取目标文件，避免父级资源回退掩盖某个语言包漏键。 */
    private PropertyResourceBundle loadBundle(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new PropertyResourceBundle(input);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationMessages {

        @Bean
        MessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();
            source.addMessage(
                    PrintErrorCode.INVALID_REQUEST.getCode(),
                    Locale.ENGLISH,
                    "Application print request: {0}");
            return source;
        }
    }
}
