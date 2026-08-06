package com.github.leyland.letool.file.resumable;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.resumable.model.ResumableUploadRequest;
import com.github.leyland.letool.file.resumable.model.UploadSession;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.transfer.InMemoryTransferProgressMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 连续分片断点续传的关键一致性测试。
 */
class ResumableUploadServiceTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证服务实例重建后可从持久化偏移继续上传，完成操作具备幂等性。
     *
     * @throws Exception 读取最终文件失败时抛出
     */
    @Test
    void shouldResumeAfterRestartAndCompleteIdempotently() throws Exception {
        byte[] content = "abcdef".getBytes(StandardCharsets.UTF_8);
        ResumableUploadService first = service();
        UploadSession session = first.create(new ResumableUploadRequest(
                "docs", "data.txt", "text/plain", content.length, sha256(content)));
        first.append(session.uploadId(), 0, 3, sha256("abc"),
                stream("abc"));

        ResumableUploadService restarted = service();
        restarted.append(session.uploadId(), 3, 3, sha256("def"),
                stream("def"));
        StoredFile completed = restarted.complete(session.uploadId());
        StoredFile repeated = restarted.complete(session.uploadId());

        assertThat(repeated).isEqualTo(completed);
        assertThat(Files.readAllBytes(temporaryDirectory.resolve("storage")
                .resolve(completed.key().replace('/', java.io.File.separatorChar))))
                .isEqualTo(content);
        assertThat(restarted.status(session.uploadId()).storedFile()).isEqualTo(completed);
    }

    /**
     * 验证摘要或偏移不可信时回滚临时文件，并继续报告最后确认偏移。
     */
    @Test
    void shouldRollbackRejectedChunkAndKeepReliableOffset() {
        ResumableUploadService service = service();
        UploadSession session = service.create(new ResumableUploadRequest(
                "docs", "data.txt", "text/plain", 6, null));

        assertThatThrownBy(() -> service.append(
                session.uploadId(), 0, 3, sha256("xxx"), stream("abc")))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_003");
        assertThat(service.status(session.uploadId()).confirmedOffset()).isZero();
        assertThat(temporaryDirectory.resolve("sessions")
                .resolve(session.uploadId() + ".part"))
                .exists()
                .hasSize(0);

        assertThatThrownBy(() -> service.append(
                session.uploadId(), 1, 3, sha256("abc"), stream("abc")))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_010");
        assertThatThrownBy(() -> service.complete(session.uploadId()))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_010");
    }

    /**
     * 创建指向同一会话目录和存储目录的新服务实例。
     *
     * @return 断点续传服务
     */
    private ResumableUploadService service() {
        FileProperties properties = new FileProperties();
        properties.getResumable().setTemporaryPath(
                temporaryDirectory.resolve("sessions").toString());
        properties.getResumable().setMaxChunkSize(DataSize.ofKilobytes(1));
        properties.getResumable().setMaxFileSize(DataSize.ofMegabytes(1));
        properties.getResumable().setSessionTtl(Duration.ofHours(1));
        Path sessionPath = temporaryDirectory.resolve("sessions");
        return new ResumableUploadService(
                new LocalUploadSessionRepository(sessionPath),
                new LocalFileStorage(temporaryDirectory.resolve("storage")),
                new InMemoryTransferProgressMonitor(
                        Duration.ofMinutes(5), 100, Duration.ZERO, 1, List.of()),
                properties);
    }

    /**
     * 创建 UTF-8 测试输入流。
     *
     * @param value 文本内容
     * @return 输入流
     */
    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算文本 SHA-256。
     *
     * @param value 文本内容
     * @return 十六进制摘要
     */
    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组 SHA-256。
     *
     * @param value 字节内容
     * @return 十六进制摘要
     */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
