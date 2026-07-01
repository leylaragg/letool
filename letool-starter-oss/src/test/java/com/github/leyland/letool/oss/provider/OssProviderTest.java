package com.github.leyland.letool.oss.provider;

import com.github.leyland.letool.oss.config.OssProperties;
import com.github.leyland.letool.oss.core.OssProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OssProvider OSS 存储提供者桩实现测试")
class OssProviderTest {

    private OssProperties.Aliyun aliyunConfig;
    private OssProperties.Minio minioConfig;
    private OssProperties.TencentCos tencentCosConfig;

    @BeforeEach
    void setUp() {
        aliyunConfig = new OssProperties.Aliyun();
        aliyunConfig.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        aliyunConfig.setAccessKeyId("test-key-id");
        aliyunConfig.setAccessKeySecret("test-key-secret");
        aliyunConfig.setBucket("test-bucket");

        minioConfig = new OssProperties.Minio();
        minioConfig.setEndpoint("http://localhost:9000");
        minioConfig.setAccessKey("minioadmin");
        minioConfig.setSecretKey("minioadmin");
        minioConfig.setBucket("test-bucket");

        tencentCosConfig = new OssProperties.TencentCos();
        tencentCosConfig.setRegion("ap-guangzhou");
        tencentCosConfig.setSecretId("test-secret-id");
        tencentCosConfig.setSecretKey("test-secret-key");
        tencentCosConfig.setBucket("test-bucket-1234567890");
    }

    @Nested
    @DisplayName("AliyunOssProvider 桩实现测试")
    class AliyunTests {

        private OssProvider provider;

        @BeforeEach
        void setUp() {
            provider = new AliyunOssProvider(aliyunConfig);
        }

        @Test
        @DisplayName("getProviderName 应返回 'aliyun'")
        void shouldReturnProviderName() {
            assertEquals("aliyun", provider.getProviderName());
        }

        @Test
        @DisplayName("upload 应返回模拟的阿里云 OSS URL")
        void uploadShouldReturnMockUrl() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            String url = provider.upload("my-bucket", "photos/test.png", stream, "image/png");
            assertTrue(url.contains("my-bucket"));
            assertTrue(url.contains("oss-cn-hangzhou.aliyuncs.com"));
            assertTrue(url.contains("photos/test.png"));
        }

        @Test
        @DisplayName("download 应返回非空 InputStream")
        void downloadShouldReturnStream() {
            InputStream stream = provider.download("my-bucket", "photos/test.png");
            assertNotNull(stream);
        }

        @Test
        @DisplayName("delete 应返回 true")
        void deleteShouldReturnTrue() {
            assertTrue(provider.delete("my-bucket", "photos/test.png"));
        }

        @Test
        @DisplayName("exists 应返回 false（桩默认不存在）")
        void existsShouldReturnFalse() {
            assertFalse(provider.exists("my-bucket", "photos/test.png"));
        }

        @Test
        @DisplayName("getPresignedUrl 应返回包含签名的模拟 URL")
        void getPresignedUrlShouldReturnMockUrl() {
            String url = provider.getPresignedUrl("my-bucket", "photos/test.png", Duration.ofHours(1));
            assertTrue(url.contains("Expires="));
            assertTrue(url.contains("Signature=mock-signature"));
            assertTrue(url.contains("photos/test.png"));
        }
    }

    @Nested
    @DisplayName("MinioProvider 桩实现测试")
    class MinioTests {

        private OssProvider provider;

        @BeforeEach
        void setUp() {
            provider = new MinioProvider(minioConfig);
        }

        @Test
        @DisplayName("getProviderName 应返回 'minio'")
        void shouldReturnProviderName() {
            assertEquals("minio", provider.getProviderName());
        }

        @Test
        @DisplayName("upload 应返回模拟的 MinIO URL")
        void uploadShouldReturnMockUrl() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            String url = provider.upload("my-bucket", "data/file.txt", stream, "text/plain");
            assertTrue(url.contains("localhost:9000"));
            assertTrue(url.contains("my-bucket"));
            assertTrue(url.contains("data/file.txt"));
        }

        @Test
        @DisplayName("download 应返回非空 InputStream")
        void downloadShouldReturnStream() {
            InputStream stream = provider.download("my-bucket", "data/file.txt");
            assertNotNull(stream);
        }

        @Test
        @DisplayName("delete 应返回 true")
        void deleteShouldReturnTrue() {
            assertTrue(provider.delete("my-bucket", "data/file.txt"));
        }

        @Test
        @DisplayName("exists 应返回 false（桩默认不存在）")
        void existsShouldReturnFalse() {
            assertFalse(provider.exists("my-bucket", "data/file.txt"));
        }

        @Test
        @DisplayName("getPresignedUrl 应返回 S3 风格的模拟预签名 URL")
        void getPresignedUrlShouldReturnS3StyleUrl() {
            String url = provider.getPresignedUrl("my-bucket", "data/file.txt", Duration.ofMinutes(30));
            assertTrue(url.contains("X-Amz-Expires="));
            assertTrue(url.contains("X-Amz-Signature=mock-signature"));
            assertTrue(url.contains("data/file.txt"));
        }
    }

    @Nested
    @DisplayName("TencentCosProvider 桩实现测试")
    class TencentCosTests {

        private OssProvider provider;

        @BeforeEach
        void setUp() {
            provider = new TencentCosProvider(tencentCosConfig);
        }

        @Test
        @DisplayName("getProviderName 应返回 'tencent-cos'")
        void shouldReturnProviderName() {
            assertEquals("tencent-cos", provider.getProviderName());
        }

        @Test
        @DisplayName("upload 应返回模拟的腾讯云 COS URL")
        void uploadShouldReturnMockUrl() {
            InputStream stream = new ByteArrayInputStream("test".getBytes());
            String url = provider.upload("my-bucket", "videos/demo.mp4", stream, "video/mp4");
            assertTrue(url.contains("myqcloud.com"));
            assertTrue(url.contains("my-bucket"));
            assertTrue(url.contains("ap-guangzhou"));
            assertTrue(url.contains("videos/demo.mp4"));
        }

        @Test
        @DisplayName("download 应返回非空 InputStream")
        void downloadShouldReturnStream() {
            InputStream stream = provider.download("my-bucket", "videos/demo.mp4");
            assertNotNull(stream);
        }

        @Test
        @DisplayName("delete 应返回 true")
        void deleteShouldReturnTrue() {
            assertTrue(provider.delete("my-bucket", "videos/demo.mp4"));
        }

        @Test
        @DisplayName("exists 应返回 false（桩默认不存在）")
        void existsShouldReturnFalse() {
            assertFalse(provider.exists("my-bucket", "videos/demo.mp4"));
        }

        @Test
        @DisplayName("getPresignedUrl 应返回包含签名的模拟 URL")
        void getPresignedUrlShouldReturnMockUrl() {
            String url = provider.getPresignedUrl("my-bucket", "videos/demo.mp4", Duration.ofHours(1));
            assertTrue(url.contains("sign=mock-signature"));
            assertTrue(url.contains("expires="));
            assertTrue(url.contains("myqcloud.com"));
        }
    }
}
