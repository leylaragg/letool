package io.github.leylaragg.letool.file.resumable;

import io.github.leylaragg.letool.file.config.FileProperties;
import io.github.leylaragg.letool.file.exception.FileErrorCode;
import io.github.leylaragg.letool.file.exception.FileException;
import io.github.leylaragg.letool.file.model.FileMetadata;
import io.github.leylaragg.letool.file.model.FileResource;
import io.github.leylaragg.letool.file.model.OverwritePolicy;
import io.github.leylaragg.letool.file.model.StoreRequest;
import io.github.leylaragg.letool.file.model.StoredFile;
import io.github.leylaragg.letool.file.resumable.model.ResumableUploadRequest;
import io.github.leylaragg.letool.file.resumable.model.UploadProgress;
import io.github.leylaragg.letool.file.resumable.model.UploadSession;
import io.github.leylaragg.letool.file.storage.FileStorageProvider;
import io.github.leylaragg.letool.file.storage.StorageKey;
import io.github.leylaragg.letool.file.transfer.TransferProgress;
import io.github.leylaragg.letool.file.transfer.TransferProgressMonitor;
import io.github.leylaragg.letool.file.transfer.TransferStatus;
import io.github.leylaragg.letool.file.transfer.TransferType;
import io.github.leylaragg.letool.file.util.FileNameUtil;
import io.github.leylaragg.letool.file.util.MimeTypeUtil;
import io.github.leylaragg.letool.file.validation.FileTypeDetector;
import io.github.leylaragg.letool.file.validation.FileValidationContext;
import io.github.leylaragg.letool.file.validation.FileValidationPolicy;
import io.github.leylaragg.letool.file.validation.MagicNumberFileTypeDetector;
import io.github.leylaragg.letool.tool.util.DigestUtil;
import io.github.leylaragg.letool.tool.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单节点连续偏移断点续传服务。
 *
 * <p>服务只接受下一分片偏移等于最后可信偏移的顺序写入。分片内容先写入本地临时文件，
 * 校验长度和摘要并强制落盘后再更新会话元数据；最终文件仍通过已配置的
 * {@link FileStorageProvider} 写入，因此业务代码无需理解底层存储协议。</p>
 */
public final class ResumableUploadService {

    private static final Logger log = LoggerFactory.getLogger(ResumableUploadService.class);
    private static final int HEADER_LIMIT = 8 * 1024;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int LOCK_STRIPES = 256;
    private static final ReentrantLock[] SESSION_LOCKS = createLocks();

    private final UploadSessionRepository repository;
    private final FileStorageProvider storageProvider;
    private final TransferProgressMonitor progressMonitor;
    private final FileProperties.Upload uploadProperties;
    private final FileProperties.Resumable resumableProperties;
    private final FileTypeDetector fileTypeDetector;
    private final List<FileValidationPolicy> validationPolicies;
    private final Path temporaryDirectory;
    private final Clock clock;

    /**
     * 使用默认轻量类型探测器创建断点续传服务。
     *
     * @param repository 会话仓库
     * @param storageProvider 最终文件存储提供者
     * @param progressMonitor 传输进度监视器
     * @param properties 文件模块配置
     */
    public ResumableUploadService(
            UploadSessionRepository repository,
            FileStorageProvider storageProvider,
            TransferProgressMonitor progressMonitor,
            FileProperties properties) {
        this(repository, storageProvider, progressMonitor, properties,
                new MagicNumberFileTypeDetector(), List.of(), Clock.systemUTC());
    }

    /**
     * 创建支持自定义类型探测与上传校验策略的断点续传服务。
     *
     * @param repository 会话仓库
     * @param storageProvider 最终文件存储提供者
     * @param progressMonitor 传输进度监视器
     * @param properties 文件模块配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 有序上传校验策略
     */
    public ResumableUploadService(
            UploadSessionRepository repository,
            FileStorageProvider storageProvider,
            TransferProgressMonitor progressMonitor,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            List<FileValidationPolicy> validationPolicies) {
        this(repository, storageProvider, progressMonitor, properties,
                fileTypeDetector, validationPolicies, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建服务，便于受控时间测试。
     *
     * @param repository 会话仓库
     * @param storageProvider 最终文件存储提供者
     * @param progressMonitor 传输进度监视器
     * @param properties 文件模块配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 有序上传校验策略
     * @param clock 时间来源
     */
    public ResumableUploadService(
            UploadSessionRepository repository,
            FileStorageProvider storageProvider,
            TransferProgressMonitor progressMonitor,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            List<FileValidationPolicy> validationPolicies,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.storageProvider = Objects.requireNonNull(storageProvider,
                "storageProvider 不能为空");
        this.progressMonitor = Objects.requireNonNull(progressMonitor,
                "progressMonitor 不能为空");
        Objects.requireNonNull(properties, "properties 不能为空");
        this.uploadProperties = properties.getUpload();
        this.resumableProperties = properties.getResumable();
        this.fileTypeDetector = Objects.requireNonNull(fileTypeDetector,
                "fileTypeDetector 不能为空");
        this.validationPolicies = validationPolicies == null
                ? List.of()
                : List.copyOf(validationPolicies);
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        validateConfiguration();
        this.temporaryDirectory = prepareTemporaryDirectory(
                resumableProperties.getTemporaryPath());
    }

    /**
     * 创建新续传会话并预留确定的最终存储键。
     *
     * @param request 会话创建请求
     * @return 已持久化会话
     */
    public UploadSession create(ResumableUploadRequest request) {
        if (request == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "request");
        }
        if (request.totalSize() <= 0
                || request.totalSize() > resumableProperties.getMaxFileSize().toBytes()) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件大小超过续传限制");
        }
        String originalName = requireSafeFileName(request.originalName());
        String extension = FileNameUtil.getExtension(originalName);
        validateExtension(extension, uploadProperties.getAllowedExtensions());
        String contentType = normalizeContentType(request.contentType(), originalName);
        validateContentType(contentType, uploadProperties.getAllowedContentTypes());
        String expectedSha256;
        try {
            expectedSha256 = UploadSession.normalizeSha256(request.finalSha256());
        } catch (IllegalArgumentException exception) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "finalSha256");
        }

        String uploadId = UUID.randomUUID().toString();
        String targetKey = StorageKey.join(
                request.directory(), FileNameUtil.generateUniqueName(originalName));
        Instant now = clock.instant();
        UploadSession session = new UploadSession(
                uploadId,
                targetKey,
                originalName,
                contentType,
                request.totalSize(),
                0,
                expectedSha256,
                null,
                TransferStatus.PAUSED,
                now,
                now,
                now.plus(resumableProperties.getSessionTtl()),
                0,
                null);
        Path partPath = partPath(uploadId);
        try {
            Files.createFile(partPath);
            UploadSession created = repository.create(session);
            progressMonitor.begin(
                    uploadId, TransferType.RESUMABLE_UPLOAD, request.totalSize(), 0);
            progressMonitor.transition(uploadId, TransferStatus.PAUSED, null);
            return created;
        } catch (FileException exception) {
            deletePartQuietly(partPath);
            throw exception;
        } catch (IOException exception) {
            deletePartQuietly(partPath);
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 查询会话及最后可信偏移。
     *
     * @param uploadId 上传会话编号
     * @return 会话快照
     */
    public UploadSession status(String uploadId) {
        UploadSession session = requireSession(uploadId);
        ensureProgress(session);
        return session;
    }

    /**
     * 从指定可信偏移追加一个完整分片。
     *
     * @param uploadId 上传会话编号
     * @param offset 分片起始偏移
     * @param chunkLength 声明分片字节数
     * @param chunkSha256 可选的分片 SHA-256 十六进制摘要
     * @param inputStream 分片输入流；方法不会关闭该流
     * @return 已确认的新偏移
     */
    public UploadProgress append(
            String uploadId,
            long offset,
            long chunkLength,
            String chunkSha256,
            InputStream inputStream) {
        if (inputStream == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "inputStream");
        }
        if (chunkLength <= 0
                || chunkLength > resumableProperties.getMaxChunkSize().toBytes()) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "分片大小超过限制");
        }
        String expectedChunkSha256;
        try {
            expectedChunkSha256 = UploadSession.normalizeSha256(chunkSha256);
        } catch (IllegalArgumentException exception) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "chunkSha256");
        }

        Path partPath = partPath(uploadId);
        ReentrantLock lock = lockFor(partPath);
        lock.lock();
        try {
            UploadSession current = requireSession(uploadId);
            requireAppendable(current);
            if (offset != current.confirmedOffset()) {
                throw stateConflict("当前可信偏移为 " + current.confirmedOffset());
            }
            if (chunkLength > current.totalSize() - current.confirmedOffset()) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "分片超过文件总大小");
            }
            ensureProgress(current);
            transitionToRunning(uploadId);
            return appendLocked(
                    current,
                    partPath,
                    chunkLength,
                    expectedChunkSha256,
                    inputStream);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 完成上传；已完成会话重复调用时返回同一个存储结果。
     *
     * @param uploadId 上传会话编号
     * @return 最终存储结果
     */
    public StoredFile complete(String uploadId) {
        Path partPath = partPath(uploadId);
        ReentrantLock lock = lockFor(partPath);
        lock.lock();
        try {
            UploadSession current = requireSession(uploadId);
            if (current.status() == TransferStatus.COMPLETED) {
                return current.storedFile();
            }
            if (current.status() != TransferStatus.PAUSED
                    && current.status() != TransferStatus.FINALIZING) {
                throw stateConflict("当前会话不能完成");
            }
            ensureProgress(current);
            UploadSession finalizing = current.status() == TransferStatus.FINALIZING
                    ? current
                    : prepareFinalizing(current, partPath);
            transitionToFinalizing(uploadId);
            StoredFile storedFile = commitOrRecover(finalizing, partPath);
            Instant now = clock.instant();
            UploadSession completed = repository.save(
                    finalizing.withState(
                            TransferStatus.COMPLETED,
                            finalizing.actualSha256(),
                            storedFile,
                            now,
                            now.plus(resumableProperties.getSessionTtl())),
                    finalizing.version());
            progressMonitor.update(uploadId, completed.totalSize());
            progressMonitor.transition(uploadId, TransferStatus.COMPLETED, null);
            deletePartQuietly(partPath);
            return completed.storedFile();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消未完成会话并删除临时分片。
     *
     * @param uploadId 上传会话编号
     */
    public void cancel(String uploadId) {
        Path partPath = partPath(uploadId);
        ReentrantLock lock = lockFor(partPath);
        lock.lock();
        try {
            UploadSession current = requireSession(uploadId);
            if (current.status() == TransferStatus.CANCELLED) {
                return;
            }
            if (current.status().isTerminal()) {
                throw stateConflict("终态会话不能取消");
            }
            Instant now = clock.instant();
            UploadSession cancelled = repository.save(
                    current.withState(
                            TransferStatus.CANCELLED,
                            current.actualSha256(),
                            null,
                            now,
                            now),
                    current.version());
            deletePartQuietly(partPath);
            transitionIfPresent(cancelled.uploadId(), TransferStatus.CANCELLED);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理当前已经过期且没有正在写入的会话。
     *
     * @return 已清理会话数量
     */
    public int cleanupExpired() {
        Instant now = clock.instant();
        int cleaned = 0;
        for (UploadSession candidate : repository.findExpired(now)) {
            Path partPath = partPath(candidate.uploadId());
            ReentrantLock lock = lockFor(partPath);
            if (!lock.tryLock()) {
                continue;
            }
            try {
                Optional<UploadSession> latest = repository.find(candidate.uploadId());
                if (latest.isEmpty() || latest.get().expiresAt().isAfter(now)) {
                    continue;
                }
                UploadSession current = latest.get();
                if (current.status() == TransferStatus.COMPLETED) {
                    if (repository.delete(current.uploadId(), current.version())) {
                        cleaned++;
                    }
                    continue;
                }
                UploadSession expired = current.status() == TransferStatus.EXPIRED
                        ? current
                        : repository.save(
                                current.withState(
                                        TransferStatus.EXPIRED,
                                        current.actualSha256(),
                                        null,
                                        now,
                                        now),
                                current.version());
                deletePartQuietly(partPath);
                if (repository.delete(expired.uploadId(), expired.version())) {
                    cleaned++;
                }
                transitionIfPresent(expired.uploadId(), TransferStatus.EXPIRED);
            } finally {
                lock.unlock();
            }
        }
        return cleaned;
    }

    /**
     * 在持有会话锁时写入并确认一个分片。
     *
     * @param current 当前会话
     * @param partPath 临时分片文件
     * @param chunkLength 声明分片长度
     * @param expectedChunkSha256 可选分片摘要
     * @param inputStream 分片输入流
     * @return 新可信偏移
     */
    private UploadProgress appendLocked(
            UploadSession current,
            Path partPath,
            long chunkLength,
            String expectedChunkSha256,
            InputStream inputStream) {
        try (FileChannel channel = FileChannel.open(
                partPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            long confirmedOffset = current.confirmedOffset();
            long physicalSize = channel.size();
            if (physicalSize < confirmedOffset) {
                throw stateConflict("临时文件短于当前可信偏移");
            }
            if (physicalSize > confirmedOffset) {
                // 上次进程可能在写入分片后、保存元数据前退出，只保留仓库已确认部分。
                channel.truncate(confirmedOffset);
            }
            channel.position(confirmedOffset);
            try {
                String actualChunkSha256 = writeChunk(
                        channel, inputStream, chunkLength);
                if (expectedChunkSha256 != null
                        && !expectedChunkSha256.equals(actualChunkSha256)) {
                    throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "分片摘要不一致");
                }
                channel.force(true);
                long nextOffset = confirmedOffset + chunkLength;
                Instant now = clock.instant();
                UploadSession saved = repository.save(
                        current.withProgress(
                                nextOffset,
                                TransferStatus.PAUSED,
                                now,
                                now.plus(resumableProperties.getSessionTtl())),
                        current.version());
                progressMonitor.update(current.uploadId(), nextOffset);
                progressMonitor.transition(
                        current.uploadId(), TransferStatus.PAUSED, null);
                return new UploadProgress(
                        current.uploadId(), nextOffset, current.totalSize(), saved.status());
            } catch (RuntimeException | IOException exception) {
                rollback(channel, confirmedOffset);
                transitionBackToPaused(current.uploadId());
                if (exception instanceof FileException fileException) {
                    throw fileException;
                }
                throw FileException.causedBy(
                        FileErrorCode.TRANSFER_FAILED, exception);
            }
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            transitionBackToPaused(current.uploadId());
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 精确读取、写入并计算分片摘要。
     *
     * @param channel 临时文件通道
     * @param inputStream 分片输入流
     * @param chunkLength 声明分片长度
     * @return 实际分片 SHA-256
     * @throws IOException 读取或写入失败时抛出
     */
    private String writeChunk(
            FileChannel channel,
            InputStream inputStream,
            long chunkLength) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = chunkLength;
        while (remaining > 0) {
            int read = inputStream.read(
                    buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "分片长度不足");
            }
            if (read == 0) {
                continue;
            }
            digest.update(buffer, 0, read);
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, read);
            while (byteBuffer.hasRemaining()) {
                channel.write(byteBuffer);
            }
            remaining -= read;
        }
        if (inputStream.read() != -1) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "分片长度超出声明");
        }
        return HexUtil.encodeHex(digest.digest());
    }

    /**
     * 校验完整文件并把会话推进到最终提交状态。
     *
     * @param current 暂停会话
     * @param partPath 临时文件
     * @return 已持久化的最终提交会话
     */
    private UploadSession prepareFinalizing(UploadSession current, Path partPath) {
        if (current.confirmedOffset() != current.totalSize()) {
            throw stateConflict("当前可信偏移为 " + current.confirmedOffset());
        }
        try {
            if (!Files.isRegularFile(partPath) || Files.size(partPath) != current.totalSize()) {
                throw stateConflict("临时文件大小与可信偏移不一致");
            }
            String actualSha256 = sha256(partPath);
            if (current.expectedSha256() != null
                    && !current.expectedSha256().equals(actualSha256)) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "完整文件摘要不一致");
            }
            validateFinalContent(partPath, current);
            Instant now = clock.instant();
            return repository.save(
                    current.withState(
                            TransferStatus.FINALIZING,
                            actualSha256,
                            null,
                            now,
                            now.plus(resumableProperties.getSessionTtl())),
                    current.version());
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 提交临时文件；目标已存在时通过大小和摘要恢复上次中断的完成操作。
     *
     * @param session 最终提交会话
     * @param partPath 临时文件
     * @return 最终存储结果
     */
    private StoredFile commitOrRecover(UploadSession session, Path partPath) {
        if (storageProvider.exists(session.targetKey())) {
            return recoverStoredFile(session);
        }
        if (!Files.isRegularFile(partPath)) {
            throw stateConflict("最终提交临时文件不存在");
        }
        try (InputStream inputStream = Files.newInputStream(partPath)) {
            StoredFile storedFile = storageProvider.store(
                    new StoreRequest(
                            session.targetKey(),
                            session.totalSize(),
                            session.originalName(),
                            session.contentType(),
                            OverwritePolicy.FAIL),
                    inputStream);
            if (storedFile.size() != session.totalSize()) {
                throw FileException.of(FileErrorCode.STORAGE_OPERATION_FAILED);
            }
            return storedFile;
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
        }
    }

    /**
     * 校验已存在目标是否就是上次成功写入的同一文件。
     *
     * @param session 最终提交会话
     * @return 恢复的存储结果
     */
    private StoredFile recoverStoredFile(UploadSession session) {
        FileMetadata metadata = storageProvider.stat(session.targetKey());
        if (metadata.directory() || metadata.size() != session.totalSize()) {
            throw stateConflict("确定目标键已被其他内容占用");
        }
        String actualSha256;
        try (FileResource resource = storageProvider.open(session.targetKey())) {
            actualSha256 = DigestUtil.sha256(resource.inputStream());
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
        }
        if (!Objects.equals(session.actualSha256(), actualSha256)) {
            throw stateConflict("确定目标键已被其他内容占用");
        }
        String storedName = session.targetKey().substring(
                session.targetKey().lastIndexOf('/') + 1);
        return new StoredFile(
                session.targetKey(),
                session.originalName(),
                storedName,
                metadata.size(),
                session.contentType(),
                actualSha256,
                metadata.lastModified());
    }

    /**
     * 在最终提交前执行与普通上传一致的文件头和用户策略校验。
     *
     * @param partPath 临时文件
     * @param session 会话快照
     */
    private void validateFinalContent(Path partPath, UploadSession session) {
        try (BufferedInputStream inputStream = new BufferedInputStream(
                Files.newInputStream(partPath), HEADER_LIMIT)) {
            inputStream.mark(HEADER_LIMIT + 1);
            byte[] header = inputStream.readNBytes(HEADER_LIMIT);
            String extension = FileNameUtil.getExtension(session.originalName());
            String detectedType = fileTypeDetector.detect(
                    header.clone(), session.originalName(), session.contentType());
            FileValidationContext context = new FileValidationContext(
                    session.originalName(),
                    session.originalName(),
                    extension,
                    session.contentType(),
                    session.totalSize(),
                    detectedType == null ? "UNKNOWN" : detectedType,
                    header);
            for (FileValidationPolicy validationPolicy : validationPolicies) {
                validationPolicy.validate(context);
            }
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
        }
    }

    /**
     * 校验会话允许继续追加且尚未过期。
     *
     * @param session 当前会话
     */
    private void requireAppendable(UploadSession session) {
        if (session.status() != TransferStatus.PAUSED) {
            throw stateConflict("当前会话不允许追加分片");
        }
        if (!session.expiresAt().isAfter(clock.instant())) {
            throw stateConflict("会话已经过期");
        }
    }

    /**
     * 恢复进程内进度记录，持久化会话始终是可信来源。
     *
     * @param session 当前会话
     */
    private void ensureProgress(UploadSession session) {
        if (progressMonitor.find(session.uploadId()).isPresent()) {
            return;
        }
        progressMonitor.begin(
                session.uploadId(),
                TransferType.RESUMABLE_UPLOAD,
                session.totalSize(),
                session.confirmedOffset());
        if (session.status() == TransferStatus.PAUSED) {
            progressMonitor.transition(session.uploadId(), TransferStatus.PAUSED, null);
        } else if (session.status() == TransferStatus.FINALIZING) {
            progressMonitor.transition(session.uploadId(), TransferStatus.FINALIZING, null);
        }
    }

    /**
     * 把暂停进度转换为运行状态。
     *
     * @param uploadId 上传会话编号
     */
    private void transitionToRunning(String uploadId) {
        TransferProgress progress = progressMonitor.find(uploadId).orElseThrow();
        if (progress.status() == TransferStatus.PAUSED) {
            progressMonitor.transition(uploadId, TransferStatus.RUNNING, null);
        }
    }

    /**
     * 把进度转换为最终提交状态。
     *
     * @param uploadId 上传会话编号
     */
    private void transitionToFinalizing(String uploadId) {
        TransferProgress progress = progressMonitor.find(uploadId).orElseThrow();
        if (progress.status() == TransferStatus.PAUSED
                || progress.status() == TransferStatus.RUNNING) {
            progressMonitor.transition(uploadId, TransferStatus.FINALIZING, null);
        }
    }

    /**
     * 分片失败时恢复暂停状态，不把仍可重试的会话误标为终态失败。
     *
     * @param uploadId 上传会话编号
     */
    private void transitionBackToPaused(String uploadId) {
        try {
            progressMonitor.find(uploadId).ifPresent(progress -> {
                if (progress.status() == TransferStatus.RUNNING) {
                    progressMonitor.transition(uploadId, TransferStatus.PAUSED, null);
                }
            });
        } catch (RuntimeException exception) {
            log.debug("恢复断点续传暂停状态失败，uploadId={}", uploadId, exception);
        }
    }

    /**
     * 在进度记录存在且尚未终止时转换状态。
     *
     * @param uploadId 上传会话编号
     * @param targetStatus 目标状态
     */
    private void transitionIfPresent(String uploadId, TransferStatus targetStatus) {
        try {
            progressMonitor.find(uploadId).ifPresent(progress -> {
                if (!progress.status().isTerminal()) {
                    progressMonitor.transition(uploadId, targetStatus, null);
                }
            });
        } catch (RuntimeException exception) {
            log.debug("更新断点续传终态失败，uploadId={}", uploadId, exception);
        }
    }

    /**
     * 回滚未被会话仓库确认的临时字节。
     *
     * @param channel 临时文件通道
     * @param confirmedOffset 最后可信偏移
     */
    private void rollback(FileChannel channel, long confirmedOffset) {
        try {
            channel.truncate(confirmedOffset);
            channel.force(true);
        } catch (IOException rollbackException) {
            log.error("回滚断点续传临时文件失败，confirmedOffset={}",
                    confirmedOffset, rollbackException);
        }
    }

    /**
     * 查询必须存在的会话。
     *
     * @param uploadId 上传会话编号
     * @return 会话快照
     */
    private UploadSession requireSession(String uploadId) {
        return repository.find(uploadId)
                .orElseThrow(() -> FileException.of(FileErrorCode.FILE_NOT_FOUND));
    }

    /**
     * 计算本地文件 SHA-256。
     *
     * @param path 文件路径
     * @return 十六进制摘要
     */
    private String sha256(Path path) {
        try {
            return DigestUtil.sha256(path);
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
        }
    }

    /**
     * 创建 SHA-256 摘要器。
     *
     * @return SHA-256 摘要器
     */
    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时缺少 SHA-256", exception);
        }
    }

    /**
     * 规范化原始文件名。
     *
     * @param originalName 原始文件名
     * @return 安全文件名
     */
    private String requireSafeFileName(String originalName) {
        String safeName = FileNameUtil.sanitize(
                FileNameUtil.extractClientFileName(originalName));
        if (safeName == null || safeName.isBlank()) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件名为空");
        }
        return safeName;
    }

    /**
     * 校验扩展名白名单。
     *
     * @param extension 文件扩展名
     * @param allowedExtensions 允许列表
     */
    private void validateExtension(String extension, List<String> allowedExtensions) {
        Set<String> normalized = normalizedSet(allowedExtensions, true);
        if (!normalized.isEmpty()
                && !normalized.contains(extension.toLowerCase(Locale.ROOT))) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件扩展名不允许");
        }
    }

    /**
     * 校验媒体类型白名单。
     *
     * @param contentType 规范媒体类型
     * @param allowedContentTypes 允许列表
     */
    private void validateContentType(
            String contentType,
            List<String> allowedContentTypes) {
        Set<String> normalized = normalizedSet(allowedContentTypes, false);
        if (!normalized.isEmpty()
                && !normalized.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件媒体类型不允许");
        }
    }

    /**
     * 规范化白名单。
     *
     * @param values 原始配置
     * @param extension 是否为扩展名配置
     * @return 小写不可变集合
     */
    private Set<String> normalizedSet(List<String> values, boolean extension) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            if (extension && candidate.startsWith(".")) {
                candidate = candidate.substring(1);
            }
            normalized.add(candidate);
        }
        return Set.copyOf(normalized);
    }

    /**
     * 规范化媒体类型。
     *
     * @param contentType 声明媒体类型
     * @param fileName 文件名
     * @return 非空媒体类型
     */
    private String normalizeContentType(String contentType, String fileName) {
        if (contentType == null || contentType.isBlank()) {
            return MimeTypeUtil.getMimeType(fileName);
        }
        int parameterIndex = contentType.indexOf(';');
        String normalized = parameterIndex < 0
                ? contentType.trim()
                : contentType.substring(0, parameterIndex).trim();
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 准备并验证临时目录。
     *
     * @param configuredPath 配置路径
     * @return 真实绝对目录
     */
    private Path prepareTemporaryDirectory(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.temporary-path");
        }
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path.toRealPath();
        } catch (IOException | RuntimeException exception) {
            throw FileException.causedBy(
                    FileErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "resumable.temporary-path");
        }
    }

    /**
     * 获取安全临时分片路径。
     *
     * @param uploadId 上传会话编号
     * @return 临时分片路径
     */
    private Path partPath(String uploadId) {
        String normalizedId;
        try {
            normalizedId = UUID.fromString(uploadId).toString();
            if (!normalizedId.equals(uploadId)) {
                throw new IllegalArgumentException("非规范 UUID");
            }
        } catch (RuntimeException exception) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "uploadId");
        }
        Path path = temporaryDirectory.resolve(normalizedId + ".part").normalize();
        if (!path.getParent().equals(temporaryDirectory)) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        return path;
    }

    /**
     * 删除临时分片且不覆盖已经完成的主流程结果。
     *
     * @param partPath 临时分片路径
     */
    private void deletePartQuietly(Path partPath) {
        try {
            Files.deleteIfExists(partPath);
        } catch (IOException exception) {
            log.warn("清理断点续传临时文件失败，fileName={}",
                    partPath.getFileName(), exception);
        }
    }

    /**
     * 校验断点续传配置。
     */
    private void validateConfiguration() {
        Duration sessionTtl = resumableProperties.getSessionTtl();
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.session-ttl");
        }
        if (resumableProperties.getMaxChunkSize() == null
                || resumableProperties.getMaxChunkSize().toBytes() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.max-chunk-size");
        }
        if (resumableProperties.getMaxFileSize() == null
                || resumableProperties.getMaxFileSize().toBytes() <= 0
                || resumableProperties.getMaxFileSize().toBytes()
                < resumableProperties.getMaxChunkSize().toBytes()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.max-file-size");
        }
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
     * 根据临时文件路径选择稳定条带锁。
     *
     * @param partPath 临时文件路径
     * @return 会话条带锁
     */
    private ReentrantLock lockFor(Path partPath) {
        return SESSION_LOCKS[Math.floorMod(partPath.hashCode(), LOCK_STRIPES)];
    }

    /**
     * 创建统一状态冲突异常。
     *
     * @param reason 安全冲突原因
     * @return 状态冲突异常
     */
    private FileException stateConflict(String reason) {
        return FileException.of(FileErrorCode.RESUMABLE_STATE_CONFLICT, reason);
    }
}
