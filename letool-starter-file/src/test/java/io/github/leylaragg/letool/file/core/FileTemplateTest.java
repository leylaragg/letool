package io.github.leylaragg.letool.file.core;

import io.github.leylaragg.letool.file.compress.ZipUtil;
import io.github.leylaragg.letool.file.config.FileProperties;
import io.github.leylaragg.letool.file.exception.FileException;
import io.github.leylaragg.letool.file.model.StoredFile;
import io.github.leylaragg.letool.file.storage.LocalFileStorage;
import io.github.leylaragg.letool.file.transfer.InMemoryTransferProgressMonitor;
import io.github.leylaragg.letool.file.transfer.TransferStatus;
import io.github.leylaragg.letool.file.validation.MagicNumberFileTypeDetector;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件业务门面的关键上传下载测试。
 */
class FileTemplateTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证上传结果只暴露逻辑键，并能够直接用于中文文件名下载。
     *
     * @throws Exception 文件传输失败时抛出
     */
    @Test
    void shouldUploadAndDownloadThroughSingleFacade() throws Exception {
        FileProperties properties = properties(DataSize.ofMegabytes(1));
        FileTemplate fileTemplate = new FileTemplate(
                new LocalFileStorage(temporaryDirectory), properties);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "C:\\fakepath\\订单.txt", "text/plain",
                "订单内容".getBytes(StandardCharsets.UTF_8));

        StoredFile storedFile = fileTemplate.upload(multipartFile, "orders/2026");
        TrackingResponse response = new TrackingResponse();
        fileTemplate.download(storedFile.key(), "订单明细.txt", response);

        assertThat(storedFile.key()).startsWith("orders/2026/").endsWith(".txt");
        assertThat(storedFile.key()).doesNotContain(temporaryDirectory.toString());
        assertThat(storedFile.originalName()).isEqualTo("订单.txt");
        assertThat(response.getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("UTF-8");
        assertThat(response.getContentLengthLong()).isEqualTo(storedFile.size());
        assertThat(response.body()).isEqualTo("订单内容".getBytes(StandardCharsets.UTF_8));
        assertThat(response.outputClosed).isFalse();
    }

    /**
     * 验证实际读取字节超过限制时上传被拒绝且不留下文件。
     */
    @Test
    void shouldRejectActualBytesBeyondConfiguredMaximum() {
        FileProperties properties = properties(DataSize.ofBytes(4));
        FileTemplate fileTemplate = new FileTemplate(
                new LocalFileStorage(temporaryDirectory), properties);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "note.txt", "text/plain", "12345".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long getSize() {
                // 模拟客户端声明值小于实际流大小，验证服务端读取上限不能被绕过。
                return 3;
            }
        };

        assertThatThrownBy(() -> fileTemplate.upload(multipartFile, "docs"))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_003");
        assertThat(fileTemplate.list("docs")).isEmpty();
    }

    /**
     * 验证门面解压操作会应用配置的实际解压大小限制。
     *
     * @throws IOException 测试归档写入失败时抛出
     */
    @Test
    void shouldApplyConfiguredArchiveLimits() throws IOException {
        FileProperties properties = properties(DataSize.ofMegabytes(1));
        properties.getArchive().setMaxEntrySize(DataSize.ofBytes(4));
        properties.getArchive().setMaxTotalSize(DataSize.ofBytes(4));
        FileTemplate fileTemplate = new FileTemplate(
                new LocalFileStorage(temporaryDirectory.resolve("storage")), properties);
        Path archive = temporaryDirectory.resolve("oversized.zip");
        Files.write(archive, ZipUtil.compressToBytes(
                "12345".getBytes(StandardCharsets.UTF_8), "data.txt"));

        assertThatThrownBy(() -> fileTemplate.decompress(
                archive, temporaryDirectory.resolve("output")))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_007");
        assertThat(temporaryDirectory.resolve("output")).doesNotExist();
    }

    /**
     * 验证调用方指定传输编号后，普通上传和下载都会产生完成进度。
     *
     * @throws Exception 文件传输失败时抛出
     */
    @Test
    void shouldTrackNamedUploadAndDownload() throws Exception {
        InMemoryTransferProgressMonitor monitor = new InMemoryTransferProgressMonitor(
                Duration.ofMinutes(5), 100, Duration.ZERO, 1, List.of());
        FileTemplate fileTemplate = new FileTemplate(
                new LocalFileStorage(temporaryDirectory),
                properties(DataSize.ofMegabytes(1)),
                new MagicNumberFileTypeDetector(),
                List.of(),
                monitor);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "note.txt", "text/plain",
                "tracked".getBytes(StandardCharsets.UTF_8));

        StoredFile storedFile = fileTemplate.upload(multipartFile, "docs", "upload-1");
        fileTemplate.download(
                storedFile.key(), "note.txt", new TrackingResponse(), "download-1");

        assertThat(monitor.find("upload-1"))
                .hasValueSatisfying(progress -> {
                    assertThat(progress.status()).isEqualTo(TransferStatus.COMPLETED);
                    assertThat(progress.transferredBytes()).isEqualTo(storedFile.size());
                });
        assertThat(monitor.find("download-1"))
                .hasValueSatisfying(progress -> {
                    assertThat(progress.status()).isEqualTo(TransferStatus.COMPLETED);
                    assertThat(progress.transferredBytes()).isEqualTo(storedFile.size());
                });
    }

    /**
     * 创建仅允许文本扩展名的测试配置。
     *
     * @param maximumSize 最大上传大小
     * @return 文件模块配置
     */
    private FileProperties properties(DataSize maximumSize) {
        FileProperties properties = new FileProperties();
        properties.getUpload().setMaxSize(maximumSize);
        properties.getUpload().setAllowedExtensions(List.of("txt"));
        return properties;
    }

    /**
     * 用于验证模块不会关闭 Servlet 输出流的响应对象。
     */
    private static final class TrackingResponse extends MockHttpServletResponse {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private boolean outputClosed;

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // 同步测试输出流不需要异步写监听器。
                }

                @Override
                public void write(int value) {
                    body.write(value);
                }

                @Override
                public void close() throws IOException {
                    outputClosed = true;
                    super.close();
                }
            };
        }

        /**
         * 获取已经写出的响应体。
         *
         * @return 响应体字节
         */
        private byte[] body() {
            return body.toByteArray();
        }
    }
}
