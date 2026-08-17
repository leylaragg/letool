package io.github.leylaragg.letool.oss.config;

import io.github.leylaragg.letool.oss.core.OssProvider;
import io.github.leylaragg.letool.oss.core.OssTemplate;
import io.github.leylaragg.letool.oss.model.OssObject;
import io.github.leylaragg.letool.oss.model.OssUploadRequest;
import io.github.leylaragg.letool.oss.model.OssUploadResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSS 公共自动配置测试。
 */
class OssAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration.class));

    /**
     * 验证模块默认关闭，不创建任何运行时入口。
     */
    @Test
    @DisplayName("OSS 模块默认不启用")
    void shouldBeDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OssTemplate.class);
            assertThat(context).doesNotHaveBean(OssProperties.class);
        });
    }

    /**
     * 验证启用模块并提供业务 Provider 后创建模板。
     */
    @Test
    @DisplayName("用户 Provider 应驱动模板自动配置")
    void shouldCreateTemplateForCustomProvider() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=custom",
                        "letool.oss.bucket=assets")
                .withBean(OssProvider.class, TestOssProvider::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OssTemplate.class);
                    assertThat(context.getBean(OssProperties.class).getProvider()).isEqualTo("custom");
                    assertThat(context.getBean(OssProperties.class).getBucket()).isEqualTo("assets");
                });
    }

    /**
     * 验证启用模块却没有 Provider 时启动失败，避免静默缺失能力。
     */
    @Test
    @DisplayName("启用 OSS 后缺少 Provider 应启动失败")
    void shouldFailWhenProviderIsMissing() {
        contextRunner
                .withPropertyValues(
                        "letool.oss.enabled=true",
                        "letool.oss.provider=minio",
                        "letool.oss.bucket=assets")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("OssProvider");
                });
    }

    /**
     * 验证用户可以覆盖默认模板。
     */
    @Test
    @DisplayName("用户自定义 OssTemplate 时自动配置应退让")
    void shouldBackOffForCustomTemplate() {
        TestOssProvider provider = new TestOssProvider();
        OssProperties properties = new OssProperties();
        properties.setProvider("custom");
        properties.setBucket("assets");
        OssTemplate customTemplate = new OssTemplate(provider, properties);

        contextRunner
                .withPropertyValues("letool.oss.enabled=true")
                .withBean(OssProvider.class, () -> provider)
                .withBean(OssTemplate.class, () -> customTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OssTemplate.class);
                    assertThat(context.getBean(OssTemplate.class)).isSameAs(customTemplate);
                });
    }

    /**
     * 最小测试 Provider。
     */
    private static final class TestOssProvider implements OssProvider {

        /**
         * 返回测试上传结果。
         *
         * @param request 上传请求
         * @return 上传结果
         */
        @Override
        public OssUploadResult upload(OssUploadRequest request) {
            return new OssUploadResult("test", request.getBucket(), request.getObjectKey(), null, null);
        }

        /**
         * 返回空测试对象。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         * @return 下载对象
         */
        @Override
        public OssObject download(String bucket, String objectKey) {
            return OssObject.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .content(InputStream.nullInputStream())
                    .build();
        }

        /**
         * 执行幂等删除。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         */
        @Override
        public void delete(String bucket, String objectKey) {
            // 测试 Provider 无需访问远程服务。
        }

        /**
         * 返回对象不存在。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         * @return 固定返回 {@code false}
         */
        @Override
        public boolean exists(String bucket, String objectKey) {
            return false;
        }

        /**
         * 返回测试预签名地址。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         * @param expiration 有效期
         * @return 测试 URI
         */
        @Override
        public URI getPresignedUrl(String bucket, String objectKey, Duration expiration) {
            return URI.create("https://example.test/" + objectKey);
        }

        /**
         * 获取 Provider 标识。
         *
         * @return 测试标识
         */
        @Override
        public String getProviderName() {
            return "test";
        }
    }
}
