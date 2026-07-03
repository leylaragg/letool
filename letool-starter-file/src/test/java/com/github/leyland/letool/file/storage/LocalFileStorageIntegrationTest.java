package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.download.FileDownloadService;
import com.github.leyland.letool.file.upload.FileUploadService;
import com.github.leyland.letool.file.upload.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Local filesystem integration tests for {@link LocalFileStorage}.
 *
 * <p>The tests use a JUnit temporary directory instead of external storage, keeping the file module
 * inside the toolkit boundary while still exercising real file I/O.</p>
 */
class LocalFileStorageIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the full local storage lifecycle against a real temporary directory.
     */
    @Test
    void shouldUploadDownloadListAndDeleteLocalFile() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        byte[] content = "hello letool".getBytes(StandardCharsets.UTF_8);

        String storedPath = storage.upload(new ByteArrayInputStream(content), "docs", "note.txt");

        assertThat(storedPath).startsWith(tempDir.toString());
        assertThat(storage.exists("docs/note.txt")).isTrue();
        assertThat(storage.list("docs"))
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.getName()).isEqualTo("note.txt");
                    assertThat(file.getSize()).isEqualTo(content.length);
                    assertThat(file.isDirectory()).isFalse();
                });

        try (InputStream inputStream = storage.download("docs/note.txt")) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }

        assertThat(storage.delete("docs/note.txt")).isTrue();
        assertThat(storage.exists("docs/note.txt")).isFalse();
    }

    /**
     * Verifies that callers can download with the absolute path returned by upload.
     */
    @Test
    void shouldAcceptReturnedAbsoluteStoragePathForDownload() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        String storedPath = storage.upload(
                new ByteArrayInputStream("absolute key".getBytes(StandardCharsets.UTF_8)),
                "docs",
                "absolute.txt");

        try (InputStream inputStream = storage.download(storedPath)) {
            assertThat(inputStream.readAllBytes()).isEqualTo("absolute key".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Verifies upload and download services with Spring mock HTTP objects.
     */
    @Test
    void shouldUploadAndStreamFileThroughServices() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        FileProperties properties = new FileProperties();
        properties.getUpload().setAllowedTypes(new String[]{"txt"});
        FileUploadService uploadService = new FileUploadService(storage, properties);
        FileDownloadService downloadService = new FileDownloadService(storage);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "service content".getBytes(StandardCharsets.UTF_8));

        UploadResult result = uploadService.upload(multipartFile, "service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        downloadService.download(result.getStoragePath(), "note.txt", response);

        assertThat(result.getFileName()).isEqualTo("note.txt");
        assertThat(result.getFileSize()).isEqualTo("service content".length());
        assertThat(response.getContentAsString()).isEqualTo("service content");
        assertThat(response.getHeader("Content-Disposition")).contains("note.txt");
    }

    /**
     * Verifies that relative traversal attempts cannot escape the local storage root.
     */
    @Test
    void shouldRejectPathTraversalOutsideBaseDirectory() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.resolve("base").toString());

        assertThatThrownBy(() -> storage.upload(
                new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)),
                "../outside",
                "bad.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes local storage base directory");

        assertThatThrownBy(() -> storage.download("../outside/bad.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes local storage base directory");
        assertThat(Files.exists(tempDir.resolve("outside").resolve("bad.txt"))).isFalse();
    }
}
