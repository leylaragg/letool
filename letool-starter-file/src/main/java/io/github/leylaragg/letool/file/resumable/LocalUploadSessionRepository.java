package io.github.leylaragg.letool.file.resumable;

import io.github.leylaragg.letool.file.exception.FileErrorCode;
import io.github.leylaragg.letool.file.exception.FileException;
import io.github.leylaragg.letool.file.model.StoredFile;
import io.github.leylaragg.letool.file.resumable.model.UploadSession;
import io.github.leylaragg.letool.file.transfer.TransferStatus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 使用原子 Properties 文件持久化的单节点会话仓库。
 *
 * <p>仓库只保存逻辑存储键和会话状态，不保存文件内容、业务绝对存储路径或凭据。
 * 同一 JVM 中指向相同目录的多个仓库实例共享文件级锁。</p>
 */
public final class LocalUploadSessionRepository implements UploadSessionRepository {

    private static final String METADATA_SUFFIX = ".properties";
    private static final int LOCK_STRIPES = 256;
    private static final ReentrantLock[] FILE_LOCKS = createLocks();

    private final Path rootDirectory;

    /**
     * 创建本地会话仓库。
     *
     * @param rootDirectory 会话元数据目录
     */
    public LocalUploadSessionRepository(Path rootDirectory) {
        if (rootDirectory == null) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.temporary-path");
        }
        try {
            Path normalized = rootDirectory.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            this.rootDirectory = normalized.toRealPath();
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "resumable.temporary-path");
        }
    }

    @Override
    public UploadSession create(UploadSession session) {
        if (session == null || session.version() != 0) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "session");
        }
        Path metadataPath = metadataPath(session.uploadId());
        ReentrantLock lock = lockFor(metadataPath);
        lock.lock();
        try {
            if (Files.exists(metadataPath)) {
                throw stateConflict("会话已存在");
            }
            writeAtomically(metadataPath, session);
            return session;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<UploadSession> find(String uploadId) {
        Path metadataPath = metadataPath(uploadId);
        ReentrantLock lock = lockFor(metadataPath);
        lock.lock();
        try {
            return read(metadataPath);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public UploadSession save(UploadSession session, long expectedVersion) {
        if (session == null || expectedVersion < 0
                || session.version() != expectedVersion) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "session version");
        }
        Path metadataPath = metadataPath(session.uploadId());
        ReentrantLock lock = lockFor(metadataPath);
        lock.lock();
        try {
            UploadSession current = read(metadataPath)
                    .orElseThrow(() -> FileException.of(FileErrorCode.FILE_NOT_FOUND));
            if (current.version() != expectedVersion) {
                throw stateConflict("会话版本已变化");
            }
            UploadSession saved = session.withVersion(expectedVersion + 1);
            writeAtomically(metadataPath, saved);
            return saved;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<UploadSession> findExpired(Instant now) {
        if (now == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "now");
        }
        try (Stream<Path> paths = Files.list(rootDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(METADATA_SUFFIX))
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .filter(session -> !session.expiresAt().isAfter(now))
                    .sorted(Comparator.comparing(UploadSession::expiresAt))
                    .toList();
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    @Override
    public boolean delete(String uploadId, long expectedVersion) {
        if (expectedVersion < 0) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "expectedVersion");
        }
        Path metadataPath = metadataPath(uploadId);
        ReentrantLock lock = lockFor(metadataPath);
        lock.lock();
        try {
            Optional<UploadSession> current = read(metadataPath);
            if (current.isEmpty()) {
                return false;
            }
            if (current.get().version() != expectedVersion) {
                throw stateConflict("会话版本已变化");
            }
            return Files.deleteIfExists(metadataPath);
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取并严格解析会话元数据。
     *
     * @param metadataPath 元数据文件
     * @return 会话快照；文件不存在时为空
     */
    private Optional<UploadSession> read(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                metadataPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return Optional.of(fromProperties(properties));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof FileException fileException) {
                throw fileException;
            }
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 先写同目录临时文件，再原子替换可信元数据。
     *
     * @param metadataPath 目标元数据文件
     * @param session 会话快照
     */
    private void writeAtomically(Path metadataPath, UploadSession session) {
        Path temporaryPath = null;
        try {
            temporaryPath = Files.createTempFile(rootDirectory, ".session-", ".tmp");
            Properties properties = toProperties(session);
            try (Writer writer = Files.newBufferedWriter(
                    temporaryPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, null);
            }
            try (FileChannel channel = FileChannel.open(
                    temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporaryPath,
                        metadataPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporaryPath,
                        metadataPath,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            temporaryPath = null;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖最初的持久化异常。
                }
            }
        }
    }

    /**
     * 把会话转换为稳定 Properties 字段。
     *
     * @param session 会话快照
     * @return Properties 数据
     */
    private Properties toProperties(UploadSession session) {
        Properties properties = new Properties();
        put(properties, "uploadId", session.uploadId());
        put(properties, "targetKey", session.targetKey());
        put(properties, "originalName", session.originalName());
        put(properties, "contentType", session.contentType());
        put(properties, "totalSize", session.totalSize());
        put(properties, "confirmedOffset", session.confirmedOffset());
        put(properties, "expectedSha256", session.expectedSha256());
        put(properties, "actualSha256", session.actualSha256());
        put(properties, "status", session.status().name());
        put(properties, "createdAt", session.createdAt());
        put(properties, "updatedAt", session.updatedAt());
        put(properties, "expiresAt", session.expiresAt());
        put(properties, "version", session.version());
        StoredFile storedFile = session.storedFile();
        put(properties, "stored.present", storedFile != null);
        if (storedFile != null) {
            put(properties, "stored.key", storedFile.key());
            put(properties, "stored.originalName", storedFile.originalName());
            put(properties, "stored.storedName", storedFile.storedName());
            put(properties, "stored.size", storedFile.size());
            put(properties, "stored.contentType", storedFile.contentType());
            put(properties, "stored.sha256", storedFile.sha256());
            put(properties, "stored.lastModified", storedFile.lastModified());
        }
        return properties;
    }

    /**
     * 从 Properties 严格重建会话。
     *
     * @param properties 元数据字段
     * @return 会话快照
     */
    private UploadSession fromProperties(Properties properties) {
        StoredFile storedFile = null;
        if (Boolean.parseBoolean(required(properties, "stored.present"))) {
            storedFile = new StoredFile(
                    required(properties, "stored.key"),
                    required(properties, "stored.originalName"),
                    required(properties, "stored.storedName"),
                    Long.parseLong(required(properties, "stored.size")),
                    required(properties, "stored.contentType"),
                    optional(properties, "stored.sha256"),
                    Instant.parse(required(properties, "stored.lastModified")));
        }
        return new UploadSession(
                required(properties, "uploadId"),
                required(properties, "targetKey"),
                required(properties, "originalName"),
                required(properties, "contentType"),
                Long.parseLong(required(properties, "totalSize")),
                Long.parseLong(required(properties, "confirmedOffset")),
                optional(properties, "expectedSha256"),
                optional(properties, "actualSha256"),
                TransferStatus.valueOf(required(properties, "status")),
                Instant.parse(required(properties, "createdAt")),
                Instant.parse(required(properties, "updatedAt")),
                Instant.parse(required(properties, "expiresAt")),
                Long.parseLong(required(properties, "version")),
                storedFile);
    }

    /**
     * 获取指定会话的安全元数据路径。
     *
     * @param uploadId 上传会话编号
     * @return 元数据路径
     */
    private Path metadataPath(String uploadId) {
        String normalizedId = requireUploadId(uploadId);
        Path path = rootDirectory.resolve(normalizedId + METADATA_SUFFIX).normalize();
        if (!path.getParent().equals(rootDirectory)) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        return path;
    }

    /**
     * 获取跨仓库实例共享的文件锁。
     *
     * @param metadataPath 元数据路径
     * @return 可重入文件锁
     */
    private ReentrantLock lockFor(Path metadataPath) {
        return FILE_LOCKS[Math.floorMod(metadataPath.hashCode(), LOCK_STRIPES)];
    }

    /**
     * 创建固定数量的共享条带锁，避免按会话永久积累锁对象。
     *
     * @return 条带锁数组
     */
    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    /**
     * 校验上传编号。
     *
     * @param uploadId 上传会话编号
     * @return 规范上传编号
     */
    private String requireUploadId(String uploadId) {
        try {
            String normalized = UUID.fromString(uploadId).toString();
            if (!normalized.equals(uploadId)) {
                throw new IllegalArgumentException("非规范 UUID");
            }
            return normalized;
        } catch (RuntimeException exception) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "uploadId");
        }
    }

    /**
     * 写入非空属性值。
     *
     * @param properties 目标 Properties
     * @param key 属性键
     * @param value 属性值；空值不会写入
     */
    private void put(Properties properties, String key, Object value) {
        if (value != null) {
            properties.setProperty(key, value.toString());
        }
    }

    /**
     * 读取必填属性。
     *
     * @param properties Properties 数据
     * @param key 属性键
     * @return 非空属性值
     */
    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("会话元数据缺少字段：" + key);
        }
        return value;
    }

    /**
     * 读取可选属性。
     *
     * @param properties Properties 数据
     * @param key 属性键
     * @return 属性值；不存在时为空
     */
    private String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 创建包含安全冲突原因的统一异常。
     *
     * @param reason 冲突原因
     * @return 文件状态冲突异常
     */
    private FileException stateConflict(String reason) {
        return FileException.of(FileErrorCode.RESUMABLE_STATE_CONFLICT, reason);
    }
}
