package com.github.leyland.letool.oss.config;

import com.github.leyland.letool.oss.core.OssProvider;
import com.github.leyland.letool.oss.core.OssTemplate;
import com.github.leyland.letool.oss.provider.MinioProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OssAutoConfiguration} 的自动装配契约测试。
 *
 * <p>OSS 当前只内置 stub provider。测试要求 stub 必须显式开启，避免业务项目误以为
 * 配置真实 endpoint、bucket 和密钥后就已经接入真实对象存储。</p>
 */
class OssAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证 OSS 默认保持未启用状态，不因引入 starter 就创建 stub provider。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OssProvider.class);
            assertThat(context).doesNotHaveBean(OssTemplate.class);
        });
    }

    /**
     * 验证启用 OSS 但未显式允许 stub 时会 fail-fast。
     */
    @Test
    void shouldFailFastWhenEnabledWithoutStubModeOrCustomProvider() {
        contextRunner
                .withPropertyValues("letool.oss.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("未启用 stub")
                            .hasMessageContaining("自定义 OssProvider");
                });
    }

    /**
     * 验证显式开启 stub 模式后，才会创建内置 stub provider。
     */
    @Test
    void shouldCreateStubProviderWhenStubModeIsExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.stub-enabled=true",
                        "letool.oss.default-provider=minio")
                .run(context -> {
                    assertThat(context).hasSingleBean(OssProvider.class);
                    assertThat(context.getBean(OssProvider.class)).isInstanceOf(MinioProvider.class);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证未知 provider 不会静默退回 MinIO stub。
     */
    @Test
    void shouldFailFastWhenProviderIsUnsupported() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.stub-enabled=true",
                        "letool.oss.default-provider=s3")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("s3")
                            .hasMessageContaining("不支持的 OSS provider");
                });
    }

    /**
     * 验证业务项目提供真实 provider 时，自动配置不会覆盖它。
     */
    @Test
    void shouldBackOffWhenUserProvidesOssProvider() {
        contextRunner
                .withPropertyValues("letool.oss.enabled=true")
                .withUserConfiguration(UserOssConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OssProvider.class);
                    assertThat(context.getBean(OssProvider.class))
                            .isSameAs(context.getBean("ossProvider"));
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 模拟业务项目自行接入真实 OSS SDK 的 provider。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserOssConfiguration {

        @Bean
        OssProvider ossProvider() {
            return new TestOssProvider();
        }
    }

    /**
     * 用于自动装配测试的 OSS provider，不访问真实对象存储。
     */
    static class TestOssProvider implements OssProvider {

        @Override
        public String upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
            return "test://" + bucket + "/" + objectKey;
        }

        @Override
        public InputStream download(String bucket, String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public boolean delete(String bucket, String objectKey) {
            return true;
        }

        @Override
        public boolean exists(String bucket, String objectKey) {
            return true;
        }

        @Override
        public String getPresignedUrl(String bucket, String objectKey, Duration expiration) {
            return "test://" + bucket + "/" + objectKey + "?expires=" + expiration.getSeconds();
        }

        @Override
        public String getProviderName() {
            return "test";
        }
    }
}
