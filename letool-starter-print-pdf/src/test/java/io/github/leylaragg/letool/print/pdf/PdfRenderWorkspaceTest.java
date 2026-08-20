package io.github.leylaragg.letool.print.pdf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
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

    /** 文件只能由框架编号，实际写入会同步累计活动容量。 */
    @Test
    void shouldAllocateAndWriteControlledFiles() throws IOException {
        Path root = testRoot();
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 6)) {
            Path first = workspace.allocate();
            Path second = workspace.allocate();
            try (OutputStream output = workspace.openOutput(first, 3)) {
                output.write(new byte[]{1, 2, 3});
            }
            try (OutputStream output = workspace.openOutput(second, 3)) {
                output.write(new byte[]{4, 5, 6});
            }

            assertThat(first.getFileName().toString()).isEqualTo("unit-0001.pdf");
            assertThat(second.getFileName().toString()).isEqualTo("unit-0002.pdf");
            assertThat(workspace.activeBytes()).isEqualTo(6);
            Path overflow = workspace.allocate();
            assertThatThrownBy(() -> {
                try (OutputStream output = workspace.openOutput(overflow, 3)) {
                    output.write(7);
                }
            }).isInstanceOf(PdfRenderWorkspace.CapacityExceededException.class);
            assertThat(Files.size(overflow)).isZero();
        }
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
        Files.delete(root);
    }

    /** 单文件越界不会写入本批内容，旧轮次文件丢弃后会释放额度。 */
    @Test
    void shouldRejectFileOverflowAndReleaseDiscardedFile() throws IOException {
        Path root = testRoot();
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 5)) {
            Path first = workspace.allocate();
            Path second = workspace.allocate();
            try (OutputStream output = workspace.openOutput(first, 3)) {
                output.write(new byte[]{1, 2, 3});
            }
            assertThatThrownBy(() -> {
                try (OutputStream output = workspace.openOutput(second, 2)) {
                    output.write(new byte[]{4, 5, 6});
                }
            }).isInstanceOf(PdfRenderWorkspace.CapacityExceededException.class)
                    .hasMessageContaining("容量");
            assertThat(workspace.activeBytes()).isEqualTo(3);
            assertThat(Files.size(second)).isZero();

            workspace.discard(first);
            Path replacement = workspace.allocate();
            try (OutputStream output = workspace.openOutput(replacement, 3)) {
                output.write(new byte[]{4, 5, 6});
            }
            assertThat(workspace.activeBytes()).isEqualTo(3);
        }
        Files.delete(root);
    }

    /** 同一路径不能并行或重复打开，避免容量状态被两条写入链路拆分。 */
    @Test
    void shouldRejectRepeatedOpen() throws IOException {
        Path root = testRoot();
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(root, 10)) {
            Path file = workspace.allocate();
            try (OutputStream ignored = workspace.openOutput(file, 10)) {
                assertThatThrownBy(() -> workspace.openOutput(file, 10))
                        .isInstanceOf(IOException.class);
            }
            assertThatThrownBy(() -> workspace.openOutput(file, 10))
                    .isInstanceOf(IOException.class);
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
