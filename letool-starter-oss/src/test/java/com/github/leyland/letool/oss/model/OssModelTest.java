package com.github.leyland.letool.oss.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OSS 公共请求与结果模型测试。
 */
class OssModelTest {

    /**
     * 验证上传请求会复制元数据，避免调用方在构建后修改请求内容。
     */
    @Test
    @DisplayName("上传请求应保存不可变元数据快照")
    void shouldKeepImmutableMetadataSnapshot() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant", "tenant-a");

        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("assets")
                .objectKey("images/avatar.png")
                .inputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .contentLength(3)
                .contentType("image/png")
                .metadata(metadata)
                .build();

        metadata.put("tenant", "tenant-b");

        assertThat(request.getMetadata()).containsEntry("tenant", "tenant-a");
        assertThatThrownBy(() -> request.getMetadata().put("trace", "123"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证未知长度使用 {@code -1}，并拒绝其他负数。
     */
    @Test
    @DisplayName("上传请求只允许未知长度或非负长度")
    void shouldValidateContentLength() {
        OssUploadRequest unknownLengthRequest = OssUploadRequest.builder()
                .bucket("assets")
                .objectKey("stream.bin")
                .inputStream(InputStream.nullInputStream())
                .build();

        assertThat(unknownLengthRequest.getContentLength()).isEqualTo(-1L);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OssUploadRequest.builder()
                        .bucket("assets")
                        .objectKey("stream.bin")
                        .inputStream(InputStream.nullInputStream())
                        .contentLength(-2)
                        .build())
                .withMessageContaining("contentLength");
    }

    /**
     * 验证上传请求拒绝缺失的 Bucket、对象键和输入流。
     */
    @Test
    @DisplayName("上传请求应校验必填字段")
    void shouldValidateRequiredUploadFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OssUploadRequest.builder()
                        .bucket(" ")
                        .objectKey("stream.bin")
                        .inputStream(InputStream.nullInputStream())
                        .build())
                .withMessageContaining("bucket");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OssUploadRequest.builder()
                        .bucket("assets")
                        .objectKey("")
                        .inputStream(InputStream.nullInputStream())
                        .build())
                .withMessageContaining("objectKey");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OssUploadRequest.builder()
                        .bucket("assets")
                        .objectKey("stream.bin")
                        .build())
                .withMessageContaining("inputStream");
    }

    /**
     * 验证下载对象会保存不可变元数据，并负责关闭底层响应流。
     *
     * @throws IOException 底层测试流关闭失败时抛出
     */
    @Test
    @DisplayName("下载对象应关闭底层流并保存不可变元数据")
    void shouldCloseDownloadStreamAndKeepImmutableMetadata() throws IOException {
        TrackingInputStream inputStream = new TrackingInputStream();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "origin");
        OssObject ossObject = OssObject.builder()
                .bucket("assets")
                .objectKey("reports/result.csv")
                .content(inputStream)
                .contentLength(16)
                .contentType("text/csv")
                .etag("etag-1")
                .lastModified(Instant.parse("2026-08-05T00:00:00Z"))
                .metadata(metadata)
                .build();

        metadata.put("source", "changed");
        ossObject.close();

        assertThat(inputStream.closed).isTrue();
        assertThat(ossObject.getMetadata()).containsEntry("source", "origin");
        assertThatThrownBy(() -> ossObject.getMetadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证上传结果保存稳定的对象身份信息。
     */
    @Test
    @DisplayName("上传结果应保存 Provider 与对象身份")
    void shouldKeepUploadResultIdentity() {
        OssUploadResult result = new OssUploadResult(
                "minio", "assets", "images/avatar.png", "etag-1", "version-1");

        assertThat(result.getProvider()).isEqualTo("minio");
        assertThat(result.getBucket()).isEqualTo("assets");
        assertThat(result.getObjectKey()).isEqualTo("images/avatar.png");
        assertThat(result.getEtag()).isEqualTo("etag-1");
        assertThat(result.getVersionId()).isEqualTo("version-1");
    }

    /**
     * 可记录关闭状态的测试输入流。
     */
    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        /**
         * 创建包含一字节内容的测试流。
         */
        private TrackingInputStream() {
            super(new byte[]{1});
        }

        /**
         * 关闭流并记录关闭状态。
         *
         * @throws IOException 父类关闭失败时抛出
         */
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
