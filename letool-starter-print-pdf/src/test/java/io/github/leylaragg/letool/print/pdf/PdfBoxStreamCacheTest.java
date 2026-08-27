package io.github.leylaragg.letool.print.pdf;

import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 PDFBox 大流只使用单次请求目录，并随工作区完整清理。 */
class PdfBoxStreamCacheTest {

    @Test
    void scratchFilesShouldStayInsideTheRequestWorkspace() throws Exception {
        Path root = testRoot();
        Path requestDirectory;

        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 128 * 1024L)) {
            requestDirectory = workspace.requestDirectory();
            try (PdfBoxStreamCache cache = workspace.openPdfBoxCache(64 * 1024L);
                    RandomAccessStreamCache view = cache.factory().create();
                    RandomAccess buffer = view.createBuffer()) {
                buffer.write(new byte[32 * 1024]);

                try (var files = Files.list(requestDirectory)) {
                    assertThat(files).isNotEmpty();
                }
            }
            try (var files = Files.list(requestDirectory)) {
                assertThat(files).isEmpty();
            }
        }

        assertThat(requestDirectory).doesNotExist();
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
        Files.delete(root);
    }

    /** 在构建目录内创建独立临时根，避免依赖系统临时目录权限。 */
    private Path testRoot() throws Exception {
        return Files.createDirectories(
                Path.of("target", "pdfbox-cache-test", UUID.randomUUID().toString())
                        .toAbsolutePath()
                        .normalize());
    }
}
