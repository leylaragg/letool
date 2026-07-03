package com.github.leyland.letool.file.compress;

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
 * Real ZIP round-trip tests for {@link ZipUtil}.
 *
 * <p>All files live under a JUnit temporary directory so the test remains deterministic and does
 * not depend on external services.</p>
 */
class ZipUtilIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies directory compression and extraction with nested files.
     */
    @Test
    void shouldCompressAndDecompressDirectory() throws Exception {
        Path source = tempDir.resolve("source");
        Path nested = source.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(source.resolve("root.txt"), "root", StandardCharsets.UTF_8);
        Files.writeString(nested.resolve("child.txt"), "child", StandardCharsets.UTF_8);

        Path zip = tempDir.resolve("archive.zip");
        Path output = tempDir.resolve("output");
        ZipUtil.compress(source.toString(), zip.toString());
        ZipUtil.decompress(zip.toString(), output.toString());

        assertThat(Files.readString(output.resolve("root.txt"))).isEqualTo("root");
        assertThat(Files.readString(output.resolve("nested").resolve("child.txt"))).isEqualTo("child");
    }

    /**
     * Verifies that ZIP entries cannot write files outside the extraction target.
     */
    @Test
    void shouldRejectZipSlipEntry() throws Exception {
        Path zip = tempDir.resolve("zip-slip.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            outputStream.putNextEntry(new ZipEntry("../escape.txt"));
            outputStream.write("escape".getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }

        assertThatThrownBy(() -> ZipUtil.decompress(zip.toString(), tempDir.resolve("safe").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZIP entry escapes target directory");
        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }
}
