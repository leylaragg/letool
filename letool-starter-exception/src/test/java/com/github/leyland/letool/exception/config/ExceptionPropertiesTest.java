package com.github.leyland.letool.exception.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsShouldBeSafeAndStable() {
        ExceptionProperties properties = new ExceptionProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getI18n().isEnabled()).isTrue();
        assertThat(properties.getI18n().getDefaultLocale())
                .isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(properties.getI18n().isFallbackToSystemLocale()).isFalse();
    }

    @Test
    void shouldBindEveryExceptionProperty() {
        contextRunner
                .withPropertyValues(
                        "letool.exception.enabled=true",
                        "letool.exception.i18n.enabled=false",
                        "letool.exception.i18n.default-locale=en_US",
                        "letool.exception.i18n.fallback-to-system-locale=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ExceptionProperties.class);

                            ExceptionProperties properties =
                                    context.getBean(ExceptionProperties.class);
                            assertThat(properties.isEnabled()).isTrue();
                            assertThat(properties.getI18n().isEnabled()).isFalse();
                            assertThat(properties.getI18n().getDefaultLocale())
                                    .isEqualTo(Locale.US);
                            assertThat(properties.getI18n().isFallbackToSystemLocale()).isTrue();
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExceptionProperties.class)
    static class PropertiesConfiguration {
    }
}
