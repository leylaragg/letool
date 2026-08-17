package io.github.leylaragg.letool.file.compress;

import io.github.leylaragg.letool.file.exception.FileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ZIP 处理的关键安全边界测试。
 */
class ZipUtilProductionTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证嵌套目录能够正常压缩和解压。
     *
     * @throws Exception 文件读写失败时抛出
     */
    @Test
    void shouldCompressAndDecompressNestedDirectory() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("normal").resolve("nested"));
        Files.writeString(source.getParent().resolve("root.txt"), "root", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("child.txt"), "child", StandardCharsets.UTF_8);
        Path archive = temporaryDirectory.resolve("normal.zip");
        Path target = temporaryDirectory.resolve("normal-output");

        ZipUtil.compress(source.getParent(), archive, false);
        ZipUtil.decompress(archive, target, ZipLimits.defaults());

        assertThat(Files.readString(target.resolve("root.txt"), StandardCharsets.UTF_8)).isEqualTo("root");
        assertThat(Files.readString(target.resolve("nested/child.txt"), StandardCharsets.UTF_8)).isEqualTo("child");
    }

    /**
     * 验证解压过程按实际条目大小拒绝超限归档，并清理半成品。
     *
     * @throws Exception 测试归档创建失败时抛出
     */
    @Test
    void shouldRejectEntryBeyondConfiguredLimitAndCleanOutput() throws Exception {
        Path archive = temporaryDirectory.resolve("oversized.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            outputStream.putNextEntry(new ZipEntry("large.txt"));
            outputStream.write("12345".getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        Path target = temporaryDirectory.resolve("target");

        assertThatThrownBy(() -> ZipUtil.decompress(
                archive, target, new ZipLimits(10, 4, 20)))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_007");
        assertThat(target.resolve("large.txt")).doesNotExist();
    }

    /**
     * 验证路径穿越条目不会写出目标目录。
     *
     * @throws Exception 测试归档创建失败时抛出
     */
    @Test
    void shouldRejectTraversalEntry() throws Exception {
        Path archive = temporaryDirectory.resolve("traversal.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archive))) {
            outputStream.putNextEntry(new ZipEntry("../escape.txt"));
            outputStream.write("escape".getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }

        assertThatThrownBy(() -> ZipUtil.decompress(
                archive, temporaryDirectory.resolve("safe"), ZipLimits.defaults()))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_007");
        assertThat(temporaryDirectory.resolve("escape.txt")).doesNotExist();
    }

    /**
     * 验证输出 ZIP 不能位于被压缩目录内部。
     *
     * @throws Exception 测试目录创建失败时抛出
     */
    @Test
    void shouldRejectArchiveInsideSourceDirectory() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("source"));
        Files.writeString(source.resolve("note.txt"), "note", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ZipUtil.compress(source, source.resolve("archive.zip"), false))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_007");
    }
}
