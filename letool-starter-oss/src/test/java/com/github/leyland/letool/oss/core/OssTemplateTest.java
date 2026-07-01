package com.github.leyland.letool.oss.core;

import com.github.leyland.letool.oss.config.OssProperties;
import com.github.leyland.letool.oss.exception.OssException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OssTemplate 对象存储操作模板测试")
class OssTemplateTest {

    private OssTemplate ossTemplate;
    private OssProperties properties;
    private MockProvider mockProvider;

    @BeforeEach
    void setUp() {
        properties = new OssProperties();
        properties.setDefaultProvider("minio");
        OssProperties.Minio minio = new OssProperties.Minio();
        minio.setBucket("default-bucket");
        minio.setEndpoint("http://localhost:9000");
        properties.setMinio(minio);
        properties.setAliyun(new OssProperties.Aliyun());
        properties.setTencentCos(new OssProperties.TencentCos());

        mockProvider = new MockProvider();
        ossTemplate = new OssTemplate(mockProvider, properties);
    }

    @Nested
    @DisplayName("快速操作（默认 Bucket）测试")
    class QuickOperationTests {

        @Test
        @DisplayName("upload(objectKey, stream) 应使用默认 bucket 和 octet-stream")
        void uploadShouldUseDefaultBucket() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            String result = ossTemplate.upload("photos/avatar.png", stream);
            assertNotNull(result);
            assertTrue(mockProvider.lastBucket.contains("default-bucket"));
            assertEquals("photos/avatar.png", mockProvider.lastObjectKey);
            assertEquals("application/octet-stream", mockProvider.lastContentType);
        }

        @Test
        @DisplayName("upload(objectKey, stream, contentType) 应使用指定 Content-Type")
        void uploadWithContentTypeShouldUseSpecifiedType() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            ossTemplate.upload("report.pdf", stream, "application/pdf");
            assertEquals("application/pdf", mockProvider.lastContentType);
        }

        @Test
        @DisplayName("download(objectKey) 应使用默认 bucket")
        void downloadShouldUseDefaultBucket() {
            InputStream stream = ossTemplate.download("data/export.csv");
            assertNotNull(stream);
            assertEquals("default-bucket", mockProvider.lastBucket);
            assertEquals("data/export.csv", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("delete(objectKey) 应使用默认 bucket")
        void deleteShouldUseDefaultBucket() {
            assertTrue(ossTemplate.delete("temp/file.txt"));
            assertEquals("default-bucket", mockProvider.lastBucket);
            assertEquals("temp/file.txt", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("getPresignedUrl(objectKey, expiration) 应使用默认 bucket")
        void getPresignedUrlShouldUseDefaultBucket() {
            Duration expiration = Duration.ofHours(2);
            String url = ossTemplate.getPresignedUrl("data/report.pdf", expiration);
            assertNotNull(url);
            assertEquals("default-bucket", mockProvider.lastBucket);
            assertEquals(expiration, mockProvider.lastExpiration);
        }

        @Test
        @DisplayName("exists(objectKey) 应使用默认 bucket")
        void existsShouldUseDefaultBucket() {
            assertTrue(ossTemplate.exists("photos/avatar.png"));
            assertEquals("default-bucket", mockProvider.lastBucket);
            assertEquals("photos/avatar.png", mockProvider.lastObjectKey);
        }
    }

    @Nested
    @DisplayName("完整操作（指定 Bucket）测试")
    class FullOperationTests {

        @Test
        @DisplayName("upload(bucket, objectKey, stream, contentType) 应正确传递参数")
        void fullUploadShouldPassAllParams() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            String result = ossTemplate.upload("archive", "2024/doc.pdf", stream, "application/pdf");
            assertNotNull(result);
            assertEquals("archive", mockProvider.lastBucket);
            assertEquals("2024/doc.pdf", mockProvider.lastObjectKey);
            assertEquals("application/pdf", mockProvider.lastContentType);
        }

        @Test
        @DisplayName("download(bucket, objectKey) 应正确传递参数")
        void fullDownloadShouldPassAllParams() {
            ossTemplate.download("archive", "2024/doc.pdf");
            assertEquals("archive", mockProvider.lastBucket);
            assertEquals("2024/doc.pdf", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("delete(bucket, objectKey) 应正确传递参数")
        void fullDeleteShouldPassAllParams() {
            ossTemplate.delete("archive", "2024/doc.pdf");
            assertEquals("archive", mockProvider.lastBucket);
            assertEquals("2024/doc.pdf", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("getPresignedUrl(bucket, objectKey, expiration) 应正确传递参数")
        void fullGetPresignedUrlShouldPassAllParams() {
            Duration expiration = Duration.ofHours(3);
            ossTemplate.getPresignedUrl("archive", "2024/doc.pdf", expiration);
            assertEquals("archive", mockProvider.lastBucket);
            assertEquals("2024/doc.pdf", mockProvider.lastObjectKey);
            assertEquals(expiration, mockProvider.lastExpiration);
        }

        @Test
        @DisplayName("exists(bucket, objectKey) 应正确传递参数")
        void fullExistsShouldPassAllParams() {
            ossTemplate.exists("archive", "2024/doc.pdf");
            assertEquals("archive", mockProvider.lastBucket);
            assertEquals("2024/doc.pdf", mockProvider.lastObjectKey);
        }
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("builder() 应返回 Builder 实例")
        void builderShouldReturnBuilder() {
            assertNotNull(ossTemplate.builder());
        }

        @Test
        @DisplayName("Builder.upload() 应使用 Builder 中设置的 bucket")
        void builderUploadShouldUseBuilderBucket() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            ossTemplate.builder()
                    .bucket("custom-bucket")
                    .objectKey("data/file.txt")
                    .contentType("text/plain")
                    .upload(stream);

            assertEquals("custom-bucket", mockProvider.lastBucket);
            assertEquals("data/file.txt", mockProvider.lastObjectKey);
            assertEquals("text/plain", mockProvider.lastContentType);
        }

        @Test
        @DisplayName("Builder 未设置 bucket 时应使用默认 bucket")
        void builderWithoutBucketShouldUseDefault() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            ossTemplate.builder()
                    .objectKey("data/file.txt")
                    .upload(stream);

            assertTrue(mockProvider.lastBucket.contains("default-bucket"));
        }

        @Test
        @DisplayName("Builder 未设置 contentType 时应使用默认值")
        void builderWithoutContentTypeShouldUseDefault() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            ossTemplate.builder()
                    .objectKey("data/file.txt")
                    .upload(stream);

            assertEquals("application/octet-stream", mockProvider.lastContentType);
        }

        @Test
        @DisplayName("Builder.download() 应正确执行下载")
        void builderDownloadShouldWork() {
            InputStream result = ossTemplate.builder()
                    .bucket("download-bucket")
                    .objectKey("exports/data.csv")
                    .download();

            assertNotNull(result);
            assertEquals("download-bucket", mockProvider.lastBucket);
            assertEquals("exports/data.csv", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("Builder.delete() 应正确执行删除")
        void builderDeleteShouldWork() {
            boolean result = ossTemplate.builder()
                    .objectKey("temp/remove.txt")
                    .delete();

            assertTrue(result);
            assertEquals("temp/remove.txt", mockProvider.lastObjectKey);
        }

        @Test
        @DisplayName("Builder.getPresignedUrl(expiration) 应正确生成预签名 URL")
        void builderGetPresignedUrlShouldWork() {
            Duration expiration = Duration.ofMinutes(45);
            String url = ossTemplate.builder()
                    .objectKey("secure/doc.pdf")
                    .getPresignedUrl(expiration);

            assertNotNull(url);
            assertEquals("secure/doc.pdf", mockProvider.lastObjectKey);
            assertEquals(expiration, mockProvider.lastExpiration);
        }

        @Test
        @DisplayName("Builder.exists() 应正确执行存在检查")
        void builderExistsShouldWork() {
            boolean result = ossTemplate.builder()
                    .objectKey("check/here.txt")
                    .exists();

            assertTrue(result);
            assertEquals("check/here.txt", mockProvider.lastObjectKey);
        }
    }

    @Nested
    @DisplayName("Builder 链式调用测试")
    class BuilderChainingTests {

        @Test
        @DisplayName("所有设置方法应返回 this")
        void allMethodsShouldReturnThis() {
            OssTemplate.Builder builder = ossTemplate.builder();
            assertSame(builder, builder.bucket("my-bucket"));
            assertSame(builder, builder.objectKey("file.txt"));
            assertSame(builder, builder.contentType("application/json"));
        }
    }

    @Nested
    @DisplayName("Builder 参数校验测试")
    class BuilderValidationTests {

        @Test
        @DisplayName("未设置 objectKey 应抛出 IllegalArgumentException")
        void missingObjectKeyShouldThrow() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            assertThrows(IllegalArgumentException.class, () ->
                    ossTemplate.builder()
                            .bucket("my-bucket")
                            .upload(stream));
        }

        @Test
        @DisplayName("objectKey 为空白应抛出 IllegalArgumentException")
        void blankObjectKeyShouldThrow() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            assertThrows(IllegalArgumentException.class, () ->
                    ossTemplate.builder()
                            .objectKey("   ")
                            .upload(stream));
        }
    }

    @Nested
    @DisplayName("OssTemplate 异常传播测试")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("Provider 抛出 OssException 应直接传播")
        void providerOssExceptionShouldPropagate() {
            OssTemplate failTemplate = new OssTemplate(new FailingProvider(), properties);
            OssException ex = assertThrows(OssException.class, () ->
                    failTemplate.upload("bucket", "key", new ByteArrayInputStream(new byte[0]), "text/plain"));
            assertTrue(ex.getMessage().contains("stub error"));
        }

        @Test
        @DisplayName("Provider 抛出 RuntimeException 应包装为 OssException")
        void runtimeExceptionShouldWrapAsOssException() {
            OssTemplate failTemplate = new OssTemplate(new RuntimeFailingProvider(), properties);
            OssException ex = assertThrows(OssException.class, () ->
                    failTemplate.upload("bucket", "key", new ByteArrayInputStream(new byte[0]), "text/plain"));
            assertTrue(ex.getMessage().contains("Failed to upload object"));
            assertNotNull(ex.getCause());
        }
    }

    @Nested
    @DisplayName("默认 Bucket 解析测试")
    class DefaultBucketResolutionTests {

        @Test
        @DisplayName("默认 provider 为 minio 时应解析 MinIO bucket")
        void minioDefaultShouldUseMinioBucket() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            ossTemplate.upload("data.txt", stream);
            assertTrue(mockProvider.lastBucket.contains("default-bucket"));
        }

        @Test
        @DisplayName("默认 provider 为 aliyun 时应解析 Aliyun bucket")
        void aliyunDefaultShouldUseAliyunBucket() {
            OssProperties aliyunProps = new OssProperties();
            aliyunProps.setDefaultProvider("aliyun");
            OssProperties.Aliyun aliyun = new OssProperties.Aliyun();
            aliyun.setBucket("aliyun-bucket");
            aliyunProps.setAliyun(aliyun);
            aliyunProps.setMinio(new OssProperties.Minio());
            aliyunProps.setTencentCos(new OssProperties.TencentCos());

            MockProvider aliMock = new MockProvider();
            OssTemplate aliyunTemplate = new OssTemplate(aliMock, aliyunProps);
            aliyunTemplate.upload("data.txt", new ByteArrayInputStream(new byte[0]));
            assertEquals("aliyun-bucket", aliMock.lastBucket);
        }
    }

    private static class MockProvider implements OssProvider {
        String lastBucket;
        String lastObjectKey;
        String lastContentType;
        Duration lastExpiration;

        @Override
        public String upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
            this.lastBucket = bucket;
            this.lastObjectKey = objectKey;
            this.lastContentType = contentType;
            return "https://" + bucket + "/" + objectKey;
        }

        @Override
        public InputStream download(String bucket, String objectKey) {
            this.lastBucket = bucket;
            this.lastObjectKey = objectKey;
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public boolean delete(String bucket, String objectKey) {
            this.lastBucket = bucket;
            this.lastObjectKey = objectKey;
            return true;
        }

        @Override
        public boolean exists(String bucket, String objectKey) {
            this.lastBucket = bucket;
            this.lastObjectKey = objectKey;
            return true;
        }

        @Override
        public String getPresignedUrl(String bucket, String objectKey, Duration expiration) {
            this.lastBucket = bucket;
            this.lastObjectKey = objectKey;
            this.lastExpiration = expiration;
            return "https://" + bucket + "/" + objectKey + "?expires=" + expiration.getSeconds();
        }

        @Override
        public String getProviderName() {
            return "mock";
        }
    }

    private static class FailingProvider implements OssProvider {
        @Override
        public String upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
            throw new OssException("stub error");
        }

        @Override
        public InputStream download(String bucket, String objectKey) {
            throw new OssException("stub error");
        }

        @Override
        public boolean delete(String bucket, String objectKey) {
            throw new OssException("stub error");
        }

        @Override
        public boolean exists(String bucket, String objectKey) {
            throw new OssException("stub error");
        }

        @Override
        public String getPresignedUrl(String bucket, String objectKey, Duration expiration) {
            throw new OssException("stub error");
        }

        @Override
        public String getProviderName() {
            return "failing";
        }
    }

    private static class RuntimeFailingProvider implements OssProvider {
        @Override
        public String upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
            throw new RuntimeException("unexpected");
        }

        @Override
        public InputStream download(String bucket, String objectKey) {
            throw new RuntimeException("unexpected");
        }

        @Override
        public boolean delete(String bucket, String objectKey) {
            throw new RuntimeException("unexpected");
        }

        @Override
        public boolean exists(String bucket, String objectKey) {
            throw new RuntimeException("unexpected");
        }

        @Override
        public String getPresignedUrl(String bucket, String objectKey, Duration expiration) {
            throw new RuntimeException("unexpected");
        }

        @Override
        public String getProviderName() {
            return "runtime-failing";
        }
    }
}
