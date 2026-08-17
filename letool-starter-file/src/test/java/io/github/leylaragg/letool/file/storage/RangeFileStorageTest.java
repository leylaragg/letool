package io.github.leylaragg.letool.file.storage;

import io.github.leylaragg.letool.file.exception.FileException;
import io.github.leylaragg.letool.file.model.FileMetadata;
import io.github.leylaragg.letool.file.model.FileResource;
import io.github.leylaragg.letool.file.model.OverwritePolicy;
import io.github.leylaragg.letool.file.model.StorageCapability;
import io.github.leylaragg.letool.file.model.StoreRequest;
import io.github.leylaragg.letool.file.model.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件存储单区间读取契约测试。
 */
class RangeFileStorageTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证本地存储从指定位置读取准确长度，并保留完整文件元数据。
     *
     * @throws Exception 区间流读取失败时抛出
     */
    @Test
    void shouldReadExactLocalRangeWithoutLoadingPrefix() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporaryDirectory);
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        storage.store(
                new StoreRequest("video/data.bin", content.length, "data.bin",
                        "application/octet-stream", OverwritePolicy.FAIL),
                new ByteArrayInputStream(content));

        try (FileResource resource = storage.openRange("video/data.bin", 2, 4)) {
            assertThat(resource.metadata().size()).isEqualTo(content.length);
            assertThat(resource.inputStream().readAllBytes())
                    .isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(storage.capabilities()).contains(StorageCapability.RANGE_READ);
    }

    /**
     * 验证未实现区间读取的自定义 Provider 明确报告能力不支持。
     */
    @Test
    void shouldRejectRangeForUnsupportedProvider() {
        FileStorageProvider storage = new UnsupportedRangeStorage();

        assertThatThrownBy(() -> storage.openRange("data.bin", 0, 1))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_009");
    }

    /**
     * 未声明区间读取能力的最小自定义存储。
     */
    private static final class UnsupportedRangeStorage implements FileStorageProvider {

        @Override
        public StoredFile store(StoreRequest request, InputStream inputStream) {
            return new StoredFile(request.key(), request.originalName(), "data.bin", 0,
                    request.contentType(), null, Instant.EPOCH);
        }

        @Override
        public FileResource open(String key) {
            return new FileResource(stat(key), new ByteArrayInputStream(new byte[0]));
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
            return new FileMetadata(key, "data.bin", 0, false, Instant.EPOCH,
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
