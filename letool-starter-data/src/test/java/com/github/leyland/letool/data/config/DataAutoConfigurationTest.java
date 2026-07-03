package com.github.leyland.letool.data.config;

import com.github.leyland.letool.data.core.LetoolTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Auto-configuration contract tests for {@link DataAutoConfiguration}.
 */
class DataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * Data starter should stay passive until a JdbcTemplate bean exists.
     */
    @Test
    void shouldStartWithoutJdbcTemplateAndNotCreateLetoolTemplate() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataProperties.class);
            assertThat(context).doesNotHaveBean(LetoolTemplate.class);
        });
    }

    /**
     * LetoolTemplate should be created when application JDBC infrastructure exists.
     */
    @Test
    void shouldCreateLetoolTemplateWhenJdbcTemplateExists() {
        contextRunner
                .withUserConfiguration(JdbcTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).hasSingleBean(LetoolTemplate.class);
                });
    }

    /**
     * Data starter should back off when the application owns the data template.
     */
    @Test
    void shouldBackOffWhenUserProvidesLetoolTemplate() {
        contextRunner
                .withUserConfiguration(UserDataConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LetoolTemplate.class);
                    assertThat(context.getBean(LetoolTemplate.class))
                            .isSameAs(context.getBean("letoolTemplate"));
                });
    }

    /**
     * Feature switch should disable the adapter even when JdbcTemplate is present.
     */
    @Test
    void shouldNotCreateLetoolTemplateWhenDisabled() {
        contextRunner
                .withUserConfiguration(JdbcTemplateConfiguration.class)
                .withPropertyValues("letool.data.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(LetoolTemplate.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcTemplateConfiguration {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserDataConfiguration {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        LetoolTemplate letoolTemplate() {
            return new LetoolTemplate(mock(JdbcTemplate.class), new DataProperties());
        }
    }
}
