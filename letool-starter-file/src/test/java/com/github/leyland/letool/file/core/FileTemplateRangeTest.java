package com.github.leyland.letool.file.core;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.OverwritePolicy;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.transfer.InMemoryTransferProgressMonitor;
import com.github.leyland.letool.file.transfer.TransferStatus;
import com.github.leyland.letool.file.validation.MagicNumberFileTypeDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件门面单区间 HTTP 下载适配测试。
 */
class FileTemplateRangeTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证闭区间请求返回 206、准确响应头、准确内容和完成进度。
     *
     * @throws Exception 响应写出失败时抛出
     */
    @Test
    void shouldWriteExactPartialContentAndProgress() throws Exception {
        InMemoryTransferProgressMonitor monitor = monitor();
        FileTemplate template = template(localStorageWithContent(), monitor);
        MockHttpServletResponse response = new MockHttpServletResponse();

        template.downloadRange(
                "docs/data.bin", "数据.bin", "bytes=2-5", response, "range-1");

        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 2-5/10");
        assertThat(response.getContentLengthLong()).isEqualTo(4);
        assertThat(response.getContentAsByteArray())
                .isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
        assertThat(monitor.find("range-1"))
                .hasValueSatisfying(progress -> {
                    assertThat(progress.status()).isEqualTo(TransferStatus.COMPLETED);
                    assertThat(progress.transferredBytes()).isEqualTo(4);
                });
    }

    /**
     * 验证后缀区间按照完整资源长度计算。
     *
     * @throws Exception 响应写出失败时抛出
     */
    @Test
    void shouldResolveSuffixRange() throws Exception {
        FileTemplate template = template(localStorageWithContent(), monitor());
        MockHttpServletResponse response = new MockHttpServletResponse();

        template.downloadRange(
                "docs/data.bin", "data.bin", "bytes=-3", response, "range-suffix");

        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 7-9/10");
        assertThat(response.getContentAsByteArray())
                .isEqualTo("789".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验证省略结束位置时读取到完整资源末尾。
     *
     * @throws Exception 响应写出失败时抛出
     */
    @Test
    void shouldResolveOpenEndedRange() throws Exception {
        FileTemplate template = template(localStorageWithContent(), monitor());
        MockHttpServletResponse response = new MockHttpServletResponse();

        template.downloadRange(
                "docs/data.bin", "data.bin", "bytes=6-", response, "range-open-ended");

        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 6-9/10");
        assertThat(response.getContentAsByteArray())
                .isEqualTo("6789".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验证非法、多区间和越界 Range 统一返回 416 语义且不写响应体。
     *
     * @param rangeHeader 待验证 Range 请求头
     */
    @ParameterizedTest
    @ValueSource(strings = {"invalid", "bytes=0-1,3-4", "bytes=20-30"})
    void shouldReturnRangeNotSatisfiable(String rangeHeader) {
        FileTemplate template = template(localStorageWithContent(), monitor());
        MockHttpServletResponse response = new MockHttpServletResponse();

        template.downloadRange(
                "docs/data.bin", "data.bin", rangeHeader, response, "invalid-range");

        assertThat(response.getStatus()).isEqualTo(416);
        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes */10");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /**
     * 验证门面不会为不支持随机读取的 Provider 模拟 Range。
     */
    @Test
    void shouldRejectRangeWhenProviderDoesNotSupportIt() {
        FileTemplate template = template(new UnsupportedRangeStorage(), monitor());

        assertThatThrownBy(() -> template.downloadRange(
                "data.bin",
                "data.bin",
                "bytes=0-1",
                new MockHttpServletResponse(),
                "unsupported-range"))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_009");
    }

    /**
     * 创建包含固定测试文件的本地存储。
     *
     * @return 本地存储
     */
    private LocalFileStorage localStorageWithContent() {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory);
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        if (!storage.exists("docs/data.bin")) {
            storage.store(
                    new StoreRequest("docs/data.bin", content.length, "data.bin",
                            "application/octet-stream", OverwritePolicy.FAIL),
                    new ByteArrayInputStream(content));
        }
        return storage;
    }

    /**
     * 创建支持测试配置和进度监视器的文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param monitor 进度监视器
     * @return 文件门面
     */
    private FileTemplate template(
            FileStorageProvider storageProvider,
            InMemoryTransferProgressMonitor monitor) {
        return new FileTemplate(
                storageProvider,
                new FileProperties(),
                new MagicNumberFileTypeDetector(),
                List.of(),
                monitor);
    }

    /**
     * 创建无采样延迟的测试进度监视器。
     *
     * @return 进度监视器
     */
    private InMemoryTransferProgressMonitor monitor() {
        return new InMemoryTransferProgressMonitor(
                Duration.ofMinutes(5), 100, Duration.ZERO, 1, List.of());
    }

    /**
     * 不声明随机读取能力的最小存储。
     */
    private static final class UnsupportedRangeStorage implements FileStorageProvider {

        @Override
        public StoredFile store(StoreRequest request, InputStream inputStream) {
            return new StoredFile(request.key(), request.originalName(), "data.bin", 10,
                    request.contentType(), null, Instant.EPOCH);
        }

        @Override
        public FileResource open(String key) {
            return new FileResource(stat(key),
                    new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public boolean delete(String key) {
            return false;
        }

        @Override
        public boolean exists(String key) {
            return true;
        }

        @Override
        public FileMetadata stat(String key) {
            return new FileMetadata(key, "data.bin", 10, false, Instant.EPOCH,
                    "application/octet-stream");
        }

        @Override
        public List<FileMetadata> list(String directory) {
            return List.of();
        }

        @Override
        public Set<StorageCapability> capabilities() {
            return Set.of();
        }
    }
}
