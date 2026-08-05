package com.github.leyland.letool.oss.tencent;

import com.github.leyland.letool.oss.exception.OssException;
import com.github.leyland.letool.oss.model.OssObject;
import com.github.leyland.letool.oss.model.OssUploadRequest;
import com.github.leyland.letool.oss.model.OssUploadResult;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 腾讯云 COS 官方 SDK Provider 测试。
 */
@ExtendWith(MockitoExtension.class)
class TencentCosProviderTest {

    @Mock
    private COS cosClient;

    /**
     * 验证上传请求完整映射到腾讯云 SDK。
     */
    @Test
    @DisplayName("上传应映射长度、内容类型和用户元数据")
    void shouldMapUploadRequest() {
        PutObjectResult sdkResult = new PutObjectResult();
        sdkResult.setETag("etag-1");
        sdkResult.setVersionId("v1");
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(sdkResult);
        TencentCosProvider provider = new TencentCosProvider(cosClient);
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("assets-1250000000")
                .objectKey("images/a.png")
                .inputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .contentLength(3)
                .contentType("image/png")
                .metadata(Map.of("tenant", "tenant-a"))
                .build();

        OssUploadResult result = provider.upload(request);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        PutObjectRequest sdkRequest = captor.getValue();
        assertThat(sdkRequest.getBucketName()).isEqualTo("assets-1250000000");
        assertThat(sdkRequest.getKey()).isEqualTo("images/a.png");
        assertThat(sdkRequest.getMetadata().getContentLength()).isEqualTo(3L);
        assertThat(sdkRequest.getMetadata().getContentType()).isEqualTo("image/png");
        assertThat(sdkRequest.getMetadata().getUserMetadata()).containsEntry("tenant", "tenant-a");
        assertThat(result.getProvider()).isEqualTo("tencent-cos");
        assertThat(result.getEtag()).isEqualTo("etag-1");
        assertThat(result.getVersionId()).isEqualTo("v1");
    }

    /**
     * 验证未知长度上传不会伪造 Content-Length。
     */
    @Test
    @DisplayName("未知长度上传不应设置错误长度")
    void shouldKeepUnknownContentLengthUnset() {
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        TencentCosProvider provider = new TencentCosProvider(cosClient);
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("assets-1250000000")
                .objectKey("stream.bin")
                .inputStream(new ByteArrayInputStream(new byte[]{1}))
                .build();

        provider.upload(request);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        assertThat(captor.getValue().getMetadata().getRawMetadata())
                .doesNotContainKey("Content-Length");
    }

    /**
     * 验证下载对象映射内容流和完整元数据。
     *
     * @throws Exception 关闭或读取下载流失败时抛出
     */
    @Test
    @DisplayName("下载应返回可关闭对象及完整元数据")
    void shouldMapDownloadedObject() throws Exception {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(3);
        metadata.setContentType("text/plain");
        metadata.setHeader("ETag", "etag-1");
        metadata.setLastModified(Date.from(Instant.parse("2026-08-05T01:00:00Z")));
        metadata.setUserMetadata(Map.of("tenant", "tenant-a"));
        COSObjectInputStream content = mock(COSObjectInputStream.class);
        when(content.readAllBytes()).thenReturn(new byte[]{1, 2, 3});
        COSObject sdkObject = new COSObject();
        sdkObject.setBucketName("assets-1250000000");
        sdkObject.setKey("docs/a.txt");
        sdkObject.setObjectMetadata(metadata);
        sdkObject.setObjectContent(content);
        when(cosClient.getObject("assets-1250000000", "docs/a.txt")).thenReturn(sdkObject);
        TencentCosProvider provider = new TencentCosProvider(cosClient);

        try (OssObject object = provider.download("assets-1250000000", "docs/a.txt")) {
            assertThat(object.getContentLength()).isEqualTo(3L);
            assertThat(object.getContentType()).isEqualTo("text/plain");
            assertThat(object.getEtag()).isEqualTo("etag-1");
            assertThat(object.getMetadata()).containsEntry("tenant", "tenant-a");
            assertThat(object.getContent().readAllBytes()).containsExactly(1, 2, 3);
        }
        verify(content).close();
    }

    /**
     * 验证 SDK 的不存在结果直接映射为 {@code false}。
     */
    @Test
    @DisplayName("对象不存在时 exists 应返回 false")
    void shouldReturnFalseForMissingObject() {
        when(cosClient.doesObjectExist("assets-1250000000", "a.txt")).thenReturn(false);
        TencentCosProvider provider = new TencentCosProvider(cosClient);

        assertThat(provider.exists("assets-1250000000", "a.txt")).isFalse();
    }

    /**
     * 验证 SDK 异常转换为统一错误码并保留原因链。
     */
    @Test
    @DisplayName("SDK 异常应转换为统一存在性检查异常")
    void shouldWrapUnexpectedExistsFailure() {
        IllegalStateException failure = new IllegalStateException("denied");
        when(cosClient.doesObjectExist("assets-1250000000", "a.txt")).thenThrow(failure);
        TencentCosProvider provider = new TencentCosProvider(cosClient);

        assertThatThrownBy(() -> provider.exists("assets-1250000000", "a.txt"))
                .isInstanceOfSatisfying(OssException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("OSS_EXISTS_CHECK_FAILED");
                    assertThat(exception.getCause()).isSameAs(failure);
                });
    }

    /**
     * 验证删除和预签名参数映射。
     *
     * @throws Exception 构建测试 URL 失败时抛出
     */
    @Test
    @DisplayName("删除和预签名应委托给腾讯云 SDK")
    void shouldDelegateDeleteAndPresign() throws Exception {
        when(cosClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://cos.example/a.txt?signature=test"));
        TencentCosProvider provider = new TencentCosProvider(cosClient);
        Instant before = Instant.now().plus(Duration.ofMinutes(14));

        provider.delete("assets-1250000000", "a.txt");
        URI uri = provider.getPresignedUrl(
                "assets-1250000000",
                "a.txt",
                Duration.ofMinutes(15));

        verify(cosClient).deleteObject("assets-1250000000", "a.txt");
        ArgumentCaptor<GeneratePresignedUrlRequest> captor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(cosClient).generatePresignedUrl(captor.capture());
        assertThat(captor.getValue().getBucketName()).isEqualTo("assets-1250000000");
        assertThat(captor.getValue().getKey()).isEqualTo("a.txt");
        assertThat(captor.getValue().getMethod().name()).isEqualTo("GET");
        assertThat(captor.getValue().getExpiration().toInstant()).isAfter(before);
        assertThat(uri).isEqualTo(URI.create("https://cos.example/a.txt?signature=test"));
    }
}
