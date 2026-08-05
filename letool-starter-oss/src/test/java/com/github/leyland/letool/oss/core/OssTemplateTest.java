package com.github.leyland.letool.oss.core;

import com.github.leyland.letool.oss.config.OssProperties;
import com.github.leyland.letool.oss.exception.OssException;
import com.github.leyland.letool.oss.model.OssObject;
import com.github.leyland.letool.oss.model.OssUploadRequest;
import com.github.leyland.letool.oss.model.OssUploadResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OSS 便捷模板测试。
 */
class OssTemplateTest {

    /**
     * 验证快捷上传会解析默认 Bucket，并使用未知长度与默认内容类型。
     */
    @Test
    @DisplayName("快捷上传应使用默认 Bucket")
    void shouldUploadWithDefaultBucket() {
        RecordingProvider provider = new RecordingProvider();
        OssTemplate template = createTemplate(provider, "assets");

        OssUploadResult result = template.upload(
                "images/avatar.png",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThat(result.getBucket()).isEqualTo("assets");
        assertThat(provider.uploadRequest.getBucket()).isEqualTo("assets");
        assertThat(provider.uploadRequest.getContentLength()).isEqualTo(-1L);
        assertThat(provider.uploadRequest.getContentType()).isEqualTo("application/octet-stream");
    }

    /**
     * 验证字节数组上传会自动携带准确长度。
     */
    @Test
    @DisplayName("字节数组上传应自动设置内容长度")
    void shouldUploadByteArrayWithLength() {
        RecordingProvider provider = new RecordingProvider();
        OssTemplate template = createTemplate(provider, "assets");

        template.upload("documents/readme.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(provider.uploadRequest.getContentLength()).isEqualTo(5L);
        assertThat(provider.uploadRequest.getContentType()).isEqualTo("text/plain");
    }

    /**
     * 验证文件上传会自动读取长度，并在调用完成后关闭文件流。
     *
     * @throws IOException 创建或删除测试文件失败时抛出
     */
    @Test
    @DisplayName("文件上传应自动设置长度并关闭文件流")
    void shouldUploadPathAndCloseStream() throws IOException {
        Path directory = Path.of("target", "oss-template-test");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "upload-", ".txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        RecordingProvider provider = new RecordingProvider();
        OssTemplate template = createTemplate(provider, "assets");

        try {
            template.upload("documents/readme.txt", file);

            assertThat(provider.uploadRequest.getContentLength()).isEqualTo(5L);
            assertThatThrownBy(() -> provider.uploadRequest.getInputStream().read())
                    .isInstanceOf(IOException.class);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * 验证高级上传请求会原样交给 Provider。
     */
    @Test
    @DisplayName("高级上传请求应保留元数据")
    void shouldDelegateAdvancedUploadRequest() {
        RecordingProvider provider = new RecordingProvider();
        OssTemplate template = createTemplate(provider, "assets");
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("archive")
                .objectKey("2026/report.csv")
                .inputStream(InputStream.nullInputStream())
                .contentLength(0)
                .contentType("text/csv")
                .metadata(Map.of("tenant", "tenant-a"))
                .build();

        template.upload(request);

        assertThat(provider.uploadRequest).isSameAs(request);
        assertThat(provider.uploadRequest.getMetadata()).containsEntry("tenant", "tenant-a");
    }

    /**
     * 验证下载、存在性、幂等删除和预签名操作使用默认 Bucket。
     */
    @Test
    @DisplayName("对象操作应委托给 Provider")
    void shouldDelegateObjectOperations() throws IOException {
        RecordingProvider provider = new RecordingProvider();
        OssTemplate template = createTemplate(provider, "assets");

        try (OssObject object = template.download("reports/result.csv")) {
            assertThat(object.getBucket()).isEqualTo("assets");
        }
        assertThat(template.exists("reports/result.csv")).isTrue();
        template.delete("reports/result.csv");
        assertThat(template.getPresignedUrl("reports/result.csv", Duration.ofMinutes(15)))
                .isEqualTo(URI.create("https://example.test/reports/result.csv"));
        assertThat(provider.deletedBucket).isEqualTo("assets");
        assertThat(provider.deletedObjectKey).isEqualTo("reports/result.csv");
    }

    /**
     * 验证缺少默认 Bucket 时快速失败。
     */
    @Test
    @DisplayName("快捷操作应拒绝缺失的默认 Bucket")
    void shouldRejectMissingDefaultBucket() {
        OssTemplate template = createTemplate(new RecordingProvider(), " ");

        assertThatThrownBy(() -> template.exists("reports/result.csv"))
                .isInstanceOf(OssException.class)
                .extracting("code")
                .isEqualTo("OSS_CONFIG_INVALID");
    }

    /**
     * 验证模板包装非 OSS 异常时保留原始原因。
     */
    @Test
    @DisplayName("模板应保留 Provider 异常原因链")
    void shouldWrapProviderExceptionWithCause() {
        RecordingProvider provider = new RecordingProvider();
        provider.uploadFailure = new IllegalStateException("网络不可用");
        OssTemplate template = createTemplate(provider, "assets");

        assertThatThrownBy(() -> template.upload("images/avatar.png", InputStream.nullInputStream()))
                .isInstanceOfSatisfying(OssException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("OSS_UPLOAD_FAILED");
                    assertThat(exception.getCause()).isSameAs(provider.uploadFailure);
                });
    }

    /**
     * 验证模板不重复包装 Provider 已经给出的 OSS 异常。
     */
    @Test
    @DisplayName("模板应原样传播 OSS 异常")
    void shouldPropagateOssException() {
        RecordingProvider provider = new RecordingProvider();
        OssException expected = OssException.of(
                com.github.leyland.letool.oss.exception.OssErrorCode.CONFIGURATION_INVALID,
                "测试配置错误");
        provider.uploadFailure = expected;
        OssTemplate template = createTemplate(provider, "assets");

        assertThatThrownBy(() -> template.upload("images/avatar.png", InputStream.nullInputStream()))
                .isSameAs(expected);
    }

    /**
     * 创建使用指定 Provider 和默认 Bucket 的模板。
     *
     * @param provider 测试 Provider
     * @param bucket 默认 Bucket
     * @return OSS 模板
     */
    private OssTemplate createTemplate(OssProvider provider, String bucket) {
        OssProperties properties = new OssProperties();
        properties.setProvider(provider.getProviderName());
        properties.setBucket(bucket);
        return new OssTemplate(provider, properties);
    }

    /**
     * 记录调用参数的测试 Provider。
     */
    private static final class RecordingProvider implements OssProvider {

        private OssUploadRequest uploadRequest;
        private RuntimeException uploadFailure;
        private String deletedBucket;
        private String deletedObjectKey;

        /**
         * 记录上传请求并返回稳定结果。
         *
         * @param request 上传请求
         * @return 上传结果
         */
        @Override
        public OssUploadResult upload(OssUploadRequest request) {
            if (uploadFailure != null) {
                throw uploadFailure;
            }
            this.uploadRequest = request;
            return new OssUploadResult(
                    getProviderName(), request.getBucket(), request.getObjectKey(), "etag-1", null);
        }

        /**
         * 返回测试下载对象。
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
         * 记录幂等删除参数。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         */
        @Override
        public void delete(String bucket, String objectKey) {
            this.deletedBucket = bucket;
            this.deletedObjectKey = objectKey;
        }

        /**
         * 返回对象存在。
         *
         * @param bucket Bucket 名称
         * @param objectKey 对象键
         * @return 固定返回 {@code true}
         */
        @Override
        public boolean exists(String bucket, String objectKey) {
            return true;
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
         * 获取测试 Provider 标识。
         *
         * @return Provider 标识
         */
        @Override
        public String getProviderName() {
            return "recording";
        }
    }
}
