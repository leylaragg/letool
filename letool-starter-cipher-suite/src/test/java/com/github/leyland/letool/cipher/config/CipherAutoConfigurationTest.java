package com.github.leyland.letool.cipher.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration contract tests for {@link CipherAutoConfiguration}.
 */
class CipherAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CipherAutoConfiguration.class));

    @Test
    void shouldBindDefaultCipherProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CipherProperties.class);

            CipherProperties properties = context.getBean(CipherProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getAesDefaultKeySize()).isEqualTo(256);
            assertThat(properties.getRsaDefaultKeySize()).isEqualTo(2048);
            assertThat(properties.isSmEnabled()).isTrue();
        });
    }

    @Test
    void shouldBindCustomCipherProperties() {
        contextRunner
                .withPropertyValues(
                        "letool.cipher.aes-default-key-size=128",
                        "letool.cipher.rsa-default-key-size=4096",
                        "letool.cipher.sm-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CipherProperties.class);

                    CipherProperties properties = context.getBean(CipherProperties.class);
                    assertThat(properties.getAesDefaultKeySize()).isEqualTo(128);
                    assertThat(properties.getRsaDefaultKeySize()).isEqualTo(4096);
                    assertThat(properties.isSmEnabled()).isFalse();
                });
    }

    @Test
    void shouldDisableCipherAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.cipher.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CipherProperties.class));
    }
}
