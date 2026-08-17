package io.github.leylaragg.letool.oss.tencent;

import io.github.leylaragg.letool.oss.config.OssAutoConfiguration;
import io.github.leylaragg.letool.oss.core.OssProvider;
import io.github.leylaragg.letool.oss.core.OssTemplate;
import com.qcloud.cos.COS;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.region.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 腾讯云 COS Provider 自动配置测试。
 */
class TencentCosAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TencentCosAutoConfiguration.class,
                    OssAutoConfiguration.class));

    /**
     * 验证静态凭证配置创建官方客户端、Provider 和公共模板。
     */
    @Test
    @DisplayName("腾讯云配置完整时应创建全部 OSS Bean")
    void shouldCreateTencentCosBeans() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000",
                        "letool.oss.tencent-cos.region=ap-guangzhou",
                        "letool.oss.tencent-cos.secret-id=test-id",
                        "letool.oss.tencent-cos.secret-key=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(COS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证业务凭证可以替代静态密钥配置。
     */
    @Test
    @DisplayName("用户 COSCredentials 应覆盖静态凭证")
    void shouldUseCustomCredentials() {
        COSCredentials credentials = mock(COSCredentials.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000",
                        "letool.oss.tencent-cos.region=ap-guangzhou")
                .withBean(COSCredentials.class, () -> credentials)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(COS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证腾讯云原生动态凭证 Provider 优先于静态凭证。
     */
    @Test
    @DisplayName("用户 COSCredentialsProvider 应覆盖静态凭证")
    void shouldUseNativeCredentialsProvider() {
        COSCredentialsProvider credentialsProvider = mock(COSCredentialsProvider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000",
                        "letool.oss.tencent-cos.region=ap-guangzhou")
                .withBean(COSCredentialsProvider.class, () -> credentialsProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(COS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证业务 ClientConfig 可以完整提供地域和客户端参数。
     */
    @Test
    @DisplayName("用户 ClientConfig 应允许省略 Letool 地域配置")
    void shouldUseCustomClientConfigWithoutRegionProperty() {
        ClientConfig clientConfig = new ClientConfig(new Region("ap-guangzhou"));
        clientConfig.setPrintShutdownStackTrace(false);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000",
                        "letool.oss.tencent-cos.secret-id=test-id",
                        "letool.oss.tencent-cos.secret-key=test-secret")
                .withBean(ClientConfig.class, () -> clientConfig)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(COS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证用户官方客户端优先于默认客户端。
     */
    @Test
    @DisplayName("用户 COS 客户端应覆盖默认客户端")
    void shouldBackOffForCustomClient() {
        COS customClient = mock(COS.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000")
                .withBean(COS.class, () -> customClient)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(COS.class)).isSameAs(customClient);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证业务自定义 Provider 不会触发无用的官方客户端创建。
     */
    @Test
    @DisplayName("用户 OssProvider 应跳过腾讯云客户端创建")
    void shouldNotCreateClientForCustomProvider() {
        OssProvider customProvider = mock(OssProvider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000")
                .withBean(OssProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(COS.class);
                    assertThat(context.getBean(OssProvider.class)).isSameAs(customProvider);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证选择其他 Provider 时腾讯云自动配置不生效。
     */
    @Test
    @DisplayName("选择其他 Provider 时不应创建腾讯云 Bean")
    void shouldNotCreateBeansForOtherProvider() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, () -> mock(OssProvider.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(COS.class);
                    assertThat(context).doesNotHaveBean(TencentCosProvider.class);
                });
    }

    /**
     * 验证默认客户端缺少地域时启动失败。
     */
    @Test
    @DisplayName("腾讯云必要配置缺失时应启动失败")
    void shouldFailForMissingRegion() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=tencent-cos",
                        "letool.oss.bucket=assets-1250000000",
                        "letool.oss.tencent-cos.secret-id=test-id",
                        "letool.oss.tencent-cos.secret-key=test-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("region");
                });
    }
}
