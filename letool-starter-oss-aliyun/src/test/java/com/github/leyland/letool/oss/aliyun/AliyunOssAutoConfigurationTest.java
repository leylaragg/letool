package com.github.leyland.letool.oss.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.github.leyland.letool.oss.config.OssAutoConfiguration;
import com.github.leyland.letool.oss.core.OssProvider;
import com.github.leyland.letool.oss.core.OssTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 阿里云 OSS Provider 自动配置测试。
 */
class AliyunOssAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AliyunOssAutoConfiguration.class,
                    OssAutoConfiguration.class));

    /**
     * 验证静态凭证配置创建官方客户端、Provider 和公共模板。
     */
    @Test
    @DisplayName("阿里云配置完整时应创建全部 OSS Bean")
    void shouldCreateAliyunBeans() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets",
                        "letool.oss.aliyun.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "letool.oss.aliyun.access-key-id=test-id",
                        "letool.oss.aliyun.access-key-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OSS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证业务凭证 Provider 可以替代静态密钥配置。
     */
    @Test
    @DisplayName("用户 CredentialsProvider 应覆盖静态凭证")
    void shouldUseCustomCredentialsProvider() {
        CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets",
                        "letool.oss.aliyun.endpoint=https://oss-cn-hangzhou.aliyuncs.com")
                .withBean(CredentialsProvider.class, () -> credentialsProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OSS.class);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证用户官方客户端优先于默认客户端。
     */
    @Test
    @DisplayName("用户 OSS 客户端应覆盖默认客户端")
    void shouldBackOffForCustomClient() {
        OSS customClient = mock(OSS.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets")
                .withBean(OSS.class, () -> customClient)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OSS.class)).isSameAs(customClient);
                    assertThat(context).hasSingleBean(OssProvider.class);
                });
    }

    /**
     * 验证业务自定义 Provider 不会触发无用的官方客户端创建。
     */
    @Test
    @DisplayName("用户 OssProvider 应跳过阿里云客户端创建")
    void shouldNotCreateClientForCustomProvider() {
        OssProvider customProvider = mock(OssProvider.class);

        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context.getBean(OssProvider.class)).isSameAs(customProvider);
                    assertThat(context).hasSingleBean(OssTemplate.class);
                });
    }

    /**
     * 验证选择其他 Provider 时阿里云自动配置不生效。
     */
    @Test
    @DisplayName("选择其他 Provider 时不应创建阿里云 Bean")
    void shouldNotCreateBeansForOtherProvider() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, () -> mock(OssProvider.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(AliyunOssProvider.class);
                });
    }

    /**
     * 验证默认客户端缺少 Endpoint 时启动失败。
     */
    @Test
    @DisplayName("阿里云必要配置缺失时应启动失败")
    void shouldFailForMissingProperties() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=aliyun",
                        "letool.oss.bucket=assets")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("endpoint");
                });
    }
}
