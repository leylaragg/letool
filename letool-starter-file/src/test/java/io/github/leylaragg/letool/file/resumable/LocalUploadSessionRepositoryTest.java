package io.github.leylaragg.letool.file.resumable;

import io.github.leylaragg.letool.file.exception.FileException;
import io.github.leylaragg.letool.file.resumable.model.UploadSession;
import io.github.leylaragg.letool.file.transfer.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地断点续传会话仓库的持久化边界测试。
 */
class LocalUploadSessionRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证仓库实例重建后仍能恢复最后一次原子保存的可信会话。
     *
     * @throws Exception 读取测试元数据失败时抛出
     */
    @Test
    void shouldRecoverSessionAfterRepositoryRestart() throws Exception {
        String uploadId = UUID.randomUUID().toString();
        LocalUploadSessionRepository first = new LocalUploadSessionRepository(temporaryDirectory);
        UploadSession created = first.create(session(uploadId));
        UploadSession saved = first.save(
                created.withProgress(4, TransferStatus.PAUSED, Instant.parse("2026-08-07T00:00:00Z")),
                created.version());

        LocalUploadSessionRepository restarted = new LocalUploadSessionRepository(temporaryDirectory);

        assertThat(restarted.find(uploadId)).contains(saved);
        String metadata = Files.readString(temporaryDirectory.resolve(uploadId + ".properties"));
        assertThat(metadata).doesNotContain(temporaryDirectory.toAbsolutePath().toString());
        assertThat(metadata).doesNotContain("chunk-content");
    }

    /**
     * 验证旧版本调用方不能覆盖已经推进的新会话状态。
     */
    @Test
    void shouldRejectStaleVersion() {
        String uploadId = UUID.randomUUID().toString();
        LocalUploadSessionRepository repository =
                new LocalUploadSessionRepository(temporaryDirectory);
        UploadSession created = repository.create(session(uploadId));
        repository.save(
                created.withProgress(2, TransferStatus.PAUSED, created.expiresAt()),
                created.version());

        assertThatThrownBy(() -> repository.save(
                created.withProgress(3, TransferStatus.PAUSED, created.expiresAt()),
                created.version()))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_010");
    }

    /**
     * 创建可持久化的初始会话。
     *
     * @param uploadId 上传会话编号
     * @return 初始会话
     */
    private UploadSession session(String uploadId) {
        Instant createdAt = Instant.parse("2026-08-06T00:00:00Z");
        return new UploadSession(
                uploadId,
                "docs/fixed-target.bin",
                "data.bin",
                "application/octet-stream",
                10,
                0,
                null,
                null,
                TransferStatus.PAUSED,
                createdAt,
                createdAt,
                Instant.parse("2026-08-07T00:00:00Z"),
                0,
                null);
    }
}
