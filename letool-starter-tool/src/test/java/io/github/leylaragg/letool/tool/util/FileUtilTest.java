package io.github.leylaragg.letool.tool.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolveUnderRootAcceptsRelativePathAndRejectsEscape() throws IOException {
        Path expected = temporaryDirectory.resolve("reports/2026/result.xlsx")
                .toAbsolutePath().normalize();

        assertEquals(expected, FileUtil.resolveUnderRoot(
                temporaryDirectory, "reports/2026/result.xlsx"));
        assertThrows(IllegalArgumentException.class,
                () -> FileUtil.resolveUnderRoot(temporaryDirectory, "../outside.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> FileUtil.resolveUnderRoot(temporaryDirectory,
                        temporaryDirectory.resolve("absolute.txt").toString()));
    }

    @Test
    void writeAtomicallyReplacesTargetAndLeavesNoTemporaryFile() throws IOException {
        Path target = Files.writeString(temporaryDirectory.resolve("state.txt"), "old");

        FileUtil.writeAtomically(target, true,
                output -> output.write("new".getBytes(StandardCharsets.UTF_8)));

        assertEquals("new", Files.readString(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void writeAtomicallyDoesNotReplaceTargetWhenReplacementIsDisabled() throws IOException {
        Path target = Files.writeString(temporaryDirectory.resolve("state.txt"), "old");

        assertThrows(IOException.class, () -> FileUtil.writeAtomically(target, false,
                output -> output.write("new".getBytes(StandardCharsets.UTF_8))));

        assertEquals("old", Files.readString(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failedAtomicWritePreservesOldFileAndCleansTemporaryFile() throws IOException {
        Path target = Files.writeString(temporaryDirectory.resolve("state.txt"), "old");

        assertThrows(IOException.class, () -> FileUtil.writeAtomically(target, true, output -> {
            output.write("partial".getBytes(StandardCharsets.UTF_8));
            throw new IOException("模拟写入失败");
        }));

        assertEquals("old", Files.readString(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void runtimeFailureAlsoPreservesOldFileAndCleansTemporaryFile() throws IOException {
        Path target = Files.writeString(temporaryDirectory.resolve("state.txt"), "old");

        assertThrows(IllegalStateException.class,
                () -> FileUtil.writeAtomically(target, true, output -> {
                    output.write("partial".getBytes(StandardCharsets.UTF_8));
                    throw new IllegalStateException("模拟业务写入失败");
                }));

        assertEquals("old", Files.readString(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count());
        }
    }
}
