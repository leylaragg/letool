package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.OverwritePolicy;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文件存储的关键生产边界测试。
 */
class LocalFileStorageProductionTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证本地存储返回逻辑键，并保持完整流式生命周期。
     *
     * @throws Exception 文件读取失败时抛出
     */
    @Test
    void shouldStoreRelativeKeyAndExposeMetadata() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory);
        byte[] content = "hello-file".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = storage.store(
                new StoreRequest("docs/note.txt", content.length, "note.txt",
                        "text/plain", OverwritePolicy.FAIL),
                new ByteArrayInputStream(content));

        assertThat(storedFile.key()).isEqualTo("docs/note.txt");
        assertThat(storedFile.size()).isEqualTo(content.length);
        assertThat(storedFile.sha256()).hasSize(64);
        assertThat(storage.stat("docs/note.txt").key()).isEqualTo("docs/note.txt");
        assertThat(storage.list("docs")).singleElement()
                .satisfies(metadata -> assertThat(metadata.name()).isEqualTo("note.txt"));
        try (FileResource resource = storage.open("docs/note.txt")) {
            assertThat(resource.inputStream().readAllBytes()).isEqualTo(content);
        }
    }

    /**
     * 验证绝对路径和父目录穿越无法离开存储根目录。
     */
    @Test
    void shouldRejectUnsafeStorageKeys() {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory);

        assertThatThrownBy(() -> storage.open("../outside.txt"))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_002");
        assertThatThrownBy(() -> storage.open(temporaryDirectory.resolve("absolute.txt").toString()))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_002");
    }

    /**
     * 验证写入中断不会留下临时文件或可见半成品。
     *
     * @throws IOException 目录遍历失败时抛出
     */
    @Test
    void shouldRemoveTemporaryFileWhenUploadFails() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory);
        InputStream failingInputStream = new InputStream() {
            private int readCount;

            @Override
            public int read() throws IOException {
                if (readCount++ < 2) {
                    return 'a';
                }
                throw new IOException("模拟上游中断");
            }
        };

        assertThatThrownBy(() -> storage.store(
                new StoreRequest("docs/failure.txt", -1, "failure.txt",
                        "text/plain", OverwritePolicy.FAIL),
                failingInputStream))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_005");

        Path directory = temporaryDirectory.resolve("docs");
        assertThat(Files.exists(directory.resolve("failure.txt"))).isFalse();
        if (Files.exists(directory)) {
            try (var files = Files.list(directory)) {
                assertThat(files).isEmpty();
            }
        }
    }
}
