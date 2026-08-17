package com.github.leyland.letool.print.pdf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 单次请求临时工作区的容量和清理测试。
 *
 * @author leyland
 */
class PdfRenderWorkspaceTest {

    /** 文件只能由框架编号，登记后按实际大小累计活动容量。 */
    @Test
    void shouldAllocateAndRegisterControlledFiles() throws IOException {
        Path root = testRoot();
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 6)) {
            Path first = workspace.allocate();
            Path second = workspace.allocate();
            Files.write(first, new byte[]{1, 2, 3});
            Files.write(second, new byte[]{4, 5, 6});

            workspace.register(first);
            workspace.register(second);

            assertThat(first.getFileName().toString()).isEqualTo("unit-0001.pdf");
            assertThat(second.getFileName().toString()).isEqualTo("unit-0002.pdf");
            assertThat(workspace.activeBytes()).isEqualTo(6);
            Path overflow = workspace.allocate();
            Files.write(overflow, new byte[]{7});
            assertThatThrownBy(() -> workspace.register(overflow))
                    .isInstanceOf(IOException.class);
        }
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
        Files.delete(root);
    }

    /** 首次超过容量时不写入计数，旧轮次文件丢弃后可释放额度。 */
    @Test
    void shouldRejectFirstOverflowAndReleaseDiscardedFile() throws IOException {
        Path root = testRoot();
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 5)) {
            Path first = workspace.allocate();
            Path second = workspace.allocate();
            Files.write(first, new byte[]{1, 2, 3});
            Files.write(second, new byte[]{4, 5, 6});
            workspace.register(first);

            assertThatThrownBy(() -> workspace.register(second))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("容量");
            assertThat(workspace.activeBytes()).isEqualTo(3);

            workspace.discard(first);
            workspace.register(second);
            assertThat(workspace.activeBytes()).isEqualTo(3);
        }
        Files.delete(root);
    }

    /** try-with-resources 会把清理错误挂到原始失败后，不覆盖主异常。 */
    @Test
    void shouldPreservePrimaryFailureWhenCleanupFails() throws IOException {
        Path root = testRoot();
        RuntimeException primary = new RuntimeException("primary");
        Path requestDirectory = null;
        try {
            try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 10)) {
                requestDirectory = workspace.requestDirectory();
                Files.createDirectory(requestDirectory.resolve("unexpected"));
                throw primary;
            }
        } catch (RuntimeException caught) {
            assertThat(caught).isSameAs(primary);
            assertThat(caught.getSuppressed()).hasSize(1);
        }
        Files.delete(requestDirectory.resolve("unexpected"));
        Files.delete(requestDirectory);
        Files.delete(root);
    }

    /** 在构建目录内创建不会依赖系统临时目录权限的测试根。 */
    private static Path testRoot() throws IOException {
        return Files.createDirectories(Path.of("target", "workspace-test", UUID.randomUUID().toString())
                .toAbsolutePath().normalize());
    }
}
