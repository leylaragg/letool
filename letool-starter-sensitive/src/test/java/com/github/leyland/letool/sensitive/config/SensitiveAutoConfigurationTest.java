package com.github.leyland.letool.sensitive.config;

import com.github.leyland.letool.sensitive.jackson.SensitiveModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration contract tests for {@link SensitiveAutoConfiguration}.
 */
class SensitiveAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SensitiveAutoConfiguration.class));

    /**
     * Default sensitive integrations should be active in a normal Boot classpath.
     */
    @Test
    void shouldCreateDefaultSensitiveIntegrationBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SensitiveProperties.class);
            assertThat(context).hasSingleBean(SensitiveModule.class);
            assertThat(context).hasSingleBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
        });
    }

    /**
     * Jackson integration should be optional and independently switchable.
     */
    @Test
    void shouldDisableJacksonIntegrationOnly() {
        contextRunner.withPropertyValues("letool.sensitive.jackson.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveProperties.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                    assertThat(context).hasSingleBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
                });
    }

    /**
     * Log integration should be optional and independently switchable.
     */
    @Test
    void shouldDisableLogIntegrationOnly() {
        contextRunner.withPropertyValues("letool.sensitive.log.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveProperties.class);
                    assertThat(context).hasSingleBean(SensitiveModule.class);
                    assertThat(context).doesNotHaveBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
                });
    }

    /**
     * The whole module switch should disable all sensitive runtime beans.
     */
    @Test
    void shouldDisableSensitiveAutoConfiguration() {
        contextRunner.withPropertyValues("letool.sensitive.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SensitiveProperties.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                    assertThat(context).doesNotHaveBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
                });
    }

    /**
     * Jackson integration should stay passive when Jackson is not available.
     */
    @Test
    void shouldStayPassiveWithoutJacksonObjectMapper() {
        contextRunner.withClassLoader(new FilteredClassLoader("com.fasterxml.jackson.databind.ObjectMapper"))
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveProperties.class);
                    assertThat(context).doesNotHaveBean(SensitiveModule.class);
                    assertThat(context).hasSingleBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
                });
    }

    /**
     * User-provided sensitive integration beans should win over starter defaults.
     */
    @Test
    void shouldBackOffWhenUserProvidesSensitiveIntegrationBeans() {
        contextRunner.withUserConfiguration(UserSensitiveIntegrationConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveModule.class);
                    assertThat(context).hasSingleBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class);
                    assertThat(context.getBean(SensitiveModule.class)).isSameAs(context.getBean("userSensitiveModule"));
                    assertThat(context.getBean(SensitiveAutoConfiguration.SensitiveLogInitializer.class))
                            .isSameAs(context.getBean("userSensitiveLogInitializer"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserSensitiveIntegrationConfiguration {

        /**
         * User-owned Jackson sensitive module.
         *
         * @return sensitive module.
         */
        @Bean
        SensitiveModule userSensitiveModule() {
            return new SensitiveModule();
        }

        /**
         * User-owned log integration marker.
         *
         * @return log integration marker.
         */
        @Bean
        SensitiveAutoConfiguration.SensitiveLogInitializer userSensitiveLogInitializer() {
            return new SensitiveAutoConfiguration.SensitiveLogInitializer();
        }
    }
}
