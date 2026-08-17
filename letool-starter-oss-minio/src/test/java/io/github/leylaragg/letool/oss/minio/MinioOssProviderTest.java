package io.github.leylaragg.letool.oss.minio;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.leylaragg.letool.oss.exception.OssException;
import io.github.leylaragg.letool.oss.model.OssObject;
import io.github.leylaragg.letool.oss.model.OssUploadRequest;
import io.github.leylaragg.letool.oss.model.OssUploadResult;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinIO 官方 SDK Provider 测试。
 */
@ExtendWith(MockitoExtension.class)
class MinioOssProviderTest {

    @Mock
    private MinioClient minioClient;

    /**
     * 验证空上传请求在进入 SDK 前被明确拒绝。
     */
    @Test
    @DisplayName("空上传请求应快速失败")
    void shouldRejectNullUploadRequest() {
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        assertThatThrownBy(() -> provider.upload(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    /**
     * 验证上传请求完整映射到 MinIO SDK。
     *
     * @throws Exception SDK 参数读取或模拟调用失败时抛出
     */
    @Test
    @DisplayName("上传应映射长度、内容类型和用户元数据")
    void shouldMapUploadRequest() throws Exception {
        Headers headers = new Headers.Builder().build();
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(new ObjectWriteResponse(headers, "assets", "cn", "images/a.png", "etag-1", "v1"));
        MinioOssProvider provider = new MinioOssProvider(minioClient);
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("assets")
                .objectKey("images/a.png")
                .inputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .contentLength(3)
                .contentType("image/png")
                .metadata(Map.of("tenant", "tenant-a"))
                .build();

        OssUploadResult result = provider.upload(request);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs arguments = captor.getValue();
        assertThat(arguments.bucket()).isEqualTo("assets");
        assertThat(arguments.object()).isEqualTo("images/a.png");
        assertThat(arguments.objectSize()).isEqualTo(3L);
        assertThat(arguments.contentType()).isEqualTo("image/png");
        assertThat(arguments.userMetadata().get("x-amz-meta-tenant")).containsExactly("tenant-a");
        assertThat(result.getEtag()).isEqualTo("etag-1");
        assertThat(result.getVersionId()).isEqualTo("v1");
    }

    /**
     * 验证未知长度上传使用 SDK 分片流式模式。
     *
     * @throws Exception SDK 模拟调用失败时抛出
     */
    @Test
    @DisplayName("未知长度上传应使用有界分片而不是无界缓存")
    void shouldUseMultipartForUnknownLength() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(new ObjectWriteResponse(
                        new Headers.Builder().build(), "assets", "cn", "stream.bin", "etag-1", null));
        MinioOssProvider provider = new MinioOssProvider(minioClient);
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket("assets")
                .objectKey("stream.bin")
                .inputStream(new ByteArrayInputStream(new byte[]{1}))
                .build();

        provider.upload(request);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().objectSize()).isEqualTo(-1L);
        assertThat(captor.getValue().partSize()).isGreaterThanOrEqualTo(5L * 1024 * 1024);
    }

    /**
     * 验证下载会组合 MinIO 对象流与对象元数据。
     *
     * @throws Exception SDK 模拟调用失败时抛出
     */
    @Test
    @DisplayName("下载应返回可关闭对象及完整元数据")
    void shouldMapDownloadedObject() throws Exception {
        Headers headers = new Headers.Builder()
                .add("Content-Length", "3")
                .add("Content-Type", "text/plain")
                .add("ETag", "etag-1")
                .add("Last-Modified", "Wed, 05 Aug 2026 01:00:00 GMT")
                .add("x-amz-meta-tenant", "tenant-a")
                .build();
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenReturn(new StatObjectResponse(headers, "assets", "cn", "docs/a.txt"));
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(new GetObjectResponse(
                        headers, "assets", "cn", "docs/a.txt", new ByteArrayInputStream(new byte[]{1, 2, 3})));
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        try (OssObject object = provider.download("assets", "docs/a.txt")) {
            assertThat(object.getContentLength()).isEqualTo(3L);
            assertThat(object.getContentType()).isEqualTo("text/plain");
            assertThat(object.getEtag()).isEqualTo("etag-1");
            assertThat(object.getMetadata()).containsEntry("tenant", "tenant-a");
            assertThat(object.getContent().readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    /**
     * 验证 NoSuchKey 被转换为正常的不存在结果。
     *
     * @throws Exception SDK 模拟调用失败时抛出
     */
    @Test
    @DisplayName("对象不存在时 exists 应返回 false")
    void shouldReturnFalseForMissingObject() throws Exception {
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.errorResponse()).thenReturn(new ErrorResponse(
                "NoSuchKey", "missing", "assets", "a.txt", null, null, null));
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(missing);
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        assertThat(provider.exists("assets", "a.txt")).isFalse();
    }

    /**
     * 验证其他 SDK 异常不会被误判为对象不存在。
     *
     * @throws Exception SDK 模拟调用失败时抛出
     */
    @Test
    @DisplayName("权限错误应转换为统一存在性检查异常")
    void shouldWrapUnexpectedExistsFailure() throws Exception {
        ErrorResponseException denied = mock(ErrorResponseException.class);
        when(denied.errorResponse()).thenReturn(new ErrorResponse(
                "AccessDenied", "denied", "assets", "a.txt", null, null, null));
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(denied);
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        assertThatThrownBy(() -> provider.exists("assets", "a.txt"))
                .isInstanceOfSatisfying(OssException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("OSS_EXISTS_CHECK_FAILED"));
    }

    /**
     * 验证删除和预签名参数映射。
     *
     * @throws Exception SDK 模拟调用失败时抛出
     */
    @Test
    @DisplayName("删除和预签名应委托给 MinIO SDK")
    void shouldDelegateDeleteAndPresign() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example/assets/a.txt?signature=test");
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        provider.delete("assets", "a.txt");
        URI uri = provider.getPresignedUrl("assets", "a.txt", Duration.ofMinutes(15));

        ArgumentCaptor<RemoveObjectArgs> deleteCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("assets");
        assertThat(deleteCaptor.getValue().object()).isEqualTo("a.txt");
        ArgumentCaptor<GetPresignedObjectUrlArgs> urlCaptor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(urlCaptor.capture());
        assertThat(urlCaptor.getValue().expiry()).isEqualTo(900);
        assertThat(uri).isEqualTo(URI.create("https://minio.example/assets/a.txt?signature=test"));
    }

    /**
     * 验证超过 S3 协议上限的预签名有效期在进入 SDK 前被拒绝。
     */
    @Test
    @DisplayName("预签名有效期超过七天时应返回配置异常")
    void shouldRejectPresignExpirationLongerThanSevenDays() {
        MinioOssProvider provider = new MinioOssProvider(minioClient);

        assertThatThrownBy(() -> provider.getPresignedUrl(
                "assets",
                "a.txt",
                Duration.ofDays(8)))
                .isInstanceOfSatisfying(OssException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("OSS_CONFIG_INVALID"));
    }
}
