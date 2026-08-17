package io.github.leylaragg.letool.oss.minio;

import io.github.leylaragg.letool.oss.config.OssAutoConfiguration;
import io.github.leylaragg.letool.oss.core.OssProvider;
import io.github.leylaragg.letool.oss.core.OssTemplate;
import io.minio.MinioClient;
import io.minio.credentials.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MinIO Provider 自动配置测试。
 */
class MinioOssAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MinioOssAutoConfiguration.class,
                    OssAutoConfiguration.class));

    /**
     * 验证选择 MinIO 后创建官方客户端、Provider 和公共模板。
     */
    @Test
    @DisplayName("MinIO 配置完整时应创建全部 OSS Bean")
    void shouldCreateMinioBeans() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets",
                        "letool.oss.minio.endpoint=http://localhost:9000",
                        "letool.oss.minio.access-key=minioadmin",
                        "letool.oss.minio.secret-key=minioadmin")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证业务凭证 Provider 可以替代静态密钥配置。
     */
    @Test
    @DisplayName("用户凭证 Provider 应覆盖静态密钥")
    void shouldUseCustomCredentialsProvider() {
        Provider credentialsProvider = mock(Provider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets",
                        "letool.oss.minio.endpoint=http://localhost:9000")
                .withBean(Provider.class, () -> credentialsProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证选择其他 Provider 时 MinIO 自动配置不生效。
     */
    @Test
    @DisplayName("选择其他 Provider 时不应创建 MinIO Bean")
    void shouldNotCreateBeansForOtherProvider() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, () -> mock(OssProvider.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(MinioOssProvider.class);
                });
    }

    /**
     * 验证用户提供的客户端优先于默认客户端。
     */
    @Test
    @DisplayName("用户 MinioClient 应覆盖默认客户端")
    void shouldBackOffForCustomClient() {
        MinioClient customClient = mock(MinioClient.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .withBean(MinioClient.class, () -> customClient)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context.getBean(MinioClient.class)).isSameAs(customClient);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证业务自定义 Provider 不会触发无用的官方客户端创建。
     */
    @Test
    @DisplayName("用户 OssProvider 应跳过 MinIO 客户端创建")
    void shouldNotCreateClientForCustomProvider() {
        OssProvider customProvider = mock(OssProvider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context.getBean(OssProvider.class)).isSameAs(customProvider);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证创建默认客户端时缺少连接参数会启动失败。
     */
    @Test
    @DisplayName("MinIO 必要配置缺失时应启动失败")
    void shouldFailForMissingProperties() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("endpoint");
                });
    }
}
