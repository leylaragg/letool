package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.OverwritePolicy;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.util.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 具备路径边界、临时文件和原子移动保护的本地文件存储。
 */
public final class LocalFileStorage implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Path basePath;

    /**
     * 使用字符串根目录创建本地存储。
     *
     * @param basePath 本地存储根目录
     */
    public LocalFileStorage(String basePath) {
        this(Path.of(requireBasePath(basePath)));
    }

    /**
     * 使用路径根目录创建本地存储。
     *
     * @param basePath 本地存储根目录
     */
    public LocalFileStorage(Path basePath) {
        Objects.requireNonNull(basePath, "basePath 不能为空");
        try {
            Path normalized = basePath.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            this.basePath = normalized.toRealPath();
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.CONFIGURATION_INVALID, exception, "local.base-path");
        }
    }

    /**
     * 以同目录临时文件写入内容，校验完成后再移动到目标位置。
     *
     * @param request 文件写入请求
     * @param inputStream 文件内容输入流
     * @return 实际存储结果
     */
    @Override
    public StoredFile store(StoreRequest request, InputStream inputStream) {
        Objects.requireNonNull(request, "request 不能为空");
        if (inputStream == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "inputStream");
        }
        String key = StorageKey.file(request.key());
        Path target = resolve(key);
        Path temporaryFile = null;
        try {
            ensureSecureDirectory(target.getParent());
            if (request.overwritePolicy() == OverwritePolicy.FAIL
                    && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "目标文件已存在");
            }
            temporaryFile = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            MessageDigest digest = sha256Digest();
            long actualSize = copyAndDigest(inputStream, temporaryFile, digest);
            if (request.declaredSize() >= 0 && request.declaredSize() != actualSize) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "声明大小与实际大小不一致");
            }

            // 移动前再次校验所有已存在路径段，降低并发替换符号链接的风险。
            verifyNoSymbolicLink(target.getParent());
            moveIntoPlace(temporaryFile, target, request.overwritePolicy());
            temporaryFile = null;
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new StoredFile(
                    key,
                    request.originalName(),
                    target.getFileName().toString(),
                    actualSize,
                    request.contentType(),
                    HexFormat.of().formatHex(digest.digest()),
                    attributes.lastModifiedTime().toInstant());
        } catch (FileException exception) {
            deleteTemporaryFile(temporaryFile);
            throw exception;
        } catch (IOException exception) {
            deleteTemporaryFile(temporaryFile);
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 打开本地文件读取资源。
     *
     * @param key 文件逻辑键
     * @return 可关闭文件资源
     */
    @Override
    public FileResource open(String key) {
        String normalizedKey = StorageKey.file(key);
        Path target = resolve(normalizedKey);
        FileMetadata metadata = readMetadata(normalizedKey, target);
        if (metadata.directory()) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "key 必须指向文件");
        }
        try {
            return new FileResource(metadata, Files.newInputStream(target, StandardOpenOption.READ));
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 使用文件通道定位读取本地文件区间。
     *
     * @param key 文件逻辑键
     * @param start 起始字节位置
     * @param length 区间长度
     * @return 可关闭区间资源
     */
    @Override
    public FileResource openRange(String key, long start, long length) {
        String normalizedKey = StorageKey.file(key);
        Path target = resolve(normalizedKey);
        FileMetadata metadata = readMetadata(normalizedKey, target);
        validateRange(metadata, start, length);
        try {
            FileChannel channel = FileChannel.open(target, StandardOpenOption.READ);
            channel.position(start);
            return new FileResource(
                    metadata,
                    new RangeInputStream(Channels.newInputStream(channel), length));
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 删除本地文件。
     *
     * @param key 文件逻辑键
     * @return 是否成功删除
     */
    @Override
    public boolean delete(String key) {
        Path target = resolve(StorageKey.file(key));
        try {
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 检查本地文件是否存在。
     *
     * @param key 文件逻辑键
     * @return 文件是否存在
     */
    @Override
    public boolean exists(String key) {
        Path target = resolve(StorageKey.file(key));
        return Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(target);
    }

    /**
     * 查询本地文件或目录元数据。
     *
     * @param key 文件或目录逻辑键
     * @return 文件元数据
     */
    @Override
    public FileMetadata stat(String key) {
        String normalizedKey = StorageKey.file(key);
        return readMetadata(normalizedKey, resolve(normalizedKey));
    }

    /**
     * 列出本地目录的直接子项。
     *
     * @param directory 目录逻辑键
     * @return 按逻辑键排序的元数据列表
     */
    @Override
    public List<FileMetadata> list(String directory) {
        String normalizedDirectory = StorageKey.directory(directory);
        Path target = normalizedDirectory.isEmpty() ? basePath : resolve(normalizedDirectory);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        verifyNoSymbolicLink(target);
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "directory 必须指向目录");
        }
        try (var children = Files.list(target)) {
            List<FileMetadata> result = new ArrayList<>();
            children.forEach(path -> {
                String childKey = normalizedDirectory.isEmpty()
                        ? path.getFileName().toString()
                        : normalizedDirectory + "/" + path.getFileName();
                result.add(readMetadata(childKey, path));
            });
            result.sort(Comparator.comparing(FileMetadata::key));
            return List.copyOf(result);
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 返回本地存储支持的能力。
     *
     * @return 本地存储能力集合
     */
    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.DIRECTORY_LISTING,
                StorageCapability.ATOMIC_REPLACE,
                StorageCapability.RANGE_READ);
    }

    /**
     * 校验区间位于完整文件范围内。
     *
     * @param metadata 完整文件元数据
     * @param start 起始字节位置
     * @param length 区间长度
     */
    private void validateRange(FileMetadata metadata, long start, long length) {
        if (metadata.directory() || start < 0 || length <= 0
                || start >= metadata.size() || length > metadata.size() - start) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "range");
        }
    }

    /**
     * 将安全逻辑键解析到存储根目录下。
     *
     * @param key 已规范化逻辑键
     * @return 根目录内的目标路径
     */
    private Path resolve(String key) {
        Path candidate = basePath.resolve(key.replace('/', java.io.File.separatorChar)).normalize();
        if (!candidate.startsWith(basePath)) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        verifyNoSymbolicLink(candidate);
        return candidate;
    }

    /**
     * 逐级创建并校验父目录。
     *
     * @param directory 待创建目录
     * @throws IOException 目录创建失败时抛出
     */
    private void ensureSecureDirectory(Path directory) throws IOException {
        Path current = basePath;
        Path relative = basePath.relativize(directory);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)
                        || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw FileException.of(FileErrorCode.UNSAFE_PATH);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    /**
     * 检查根目录至目标路径之间不存在符号链接。
     *
     * @param target 待检查路径
     */
    private void verifyNoSymbolicLink(Path target) {
        if (target == null || !target.normalize().startsWith(basePath)) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        Path current = basePath;
        for (Path segment : basePath.relativize(target.normalize())) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw FileException.of(FileErrorCode.UNSAFE_PATH);
            }
        }
    }

    /**
     * 读取目标路径元数据。
     *
     * @param key 逻辑键
     * @param target 本地目标路径
     * @return 文件元数据
     */
    private FileMetadata readMetadata(String key, Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw FileException.of(FileErrorCode.FILE_NOT_FOUND);
        }
        verifyNoSymbolicLink(target);
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean directory = attributes.isDirectory();
            return new FileMetadata(
                    key,
                    target.getFileName().toString(),
                    directory ? 0 : attributes.size(),
                    directory,
                    attributes.lastModifiedTime().toInstant(),
                    directory ? "application/octet-stream" : detectContentType(target));
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 将输入流复制到临时文件并计算摘要。
     *
     * @param inputStream 文件输入流
     * @param temporaryFile 临时文件
     * @param digest SHA-256 摘要器
     * @return 实际复制字节数
     * @throws IOException 复制失败时抛出
     */
    private long copyAndDigest(
            InputStream inputStream,
            Path temporaryFile,
            MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (var outputStream = Files.newOutputStream(
                temporaryFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    /**
     * 将临时文件移动到最终位置。
     *
     * @param temporaryFile 临时文件
     * @param target 最终目标
     * @param overwritePolicy 覆盖策略
     * @throws IOException 移动失败时抛出
     */
    private void moveIntoPlace(
            Path temporaryFile,
            Path target,
            OverwritePolicy overwritePolicy) throws IOException {
        CopyOption[] atomicOptions = overwritePolicy == OverwritePolicy.REPLACE
                ? new CopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        CopyOption[] fallbackOptions = overwritePolicy == OverwritePolicy.REPLACE
                ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new CopyOption[0];
        try {
            Files.move(temporaryFile, target, atomicOptions);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, fallbackOptions);
        }
    }

    /**
     * 删除失败上传的临时文件。
     *
     * @param temporaryFile 临时文件；尚未创建时允许为空
     */
    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            // 主异常优先保留；日志可用于定位并清理遗留的 .upload- 临时文件。
            log.warn("本地上传临时文件清理失败：{}", temporaryFile, exception);
        }
    }

    /**
     * 获取 SHA-256 摘要器。
     *
     * @return SHA-256 摘要器
     */
    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 探测文件媒体类型。
     *
     * @param target 本地文件路径
     * @return 文件媒体类型
     */
    private String detectContentType(Path target) {
        try {
            String detected = Files.probeContentType(target);
            return detected == null ? MimeTypeUtil.getMimeType(target.getFileName().toString()) : detected;
        } catch (IOException exception) {
            log.debug("无法探测文件媒体类型，将按扩展名回退：{}", target, exception);
            return MimeTypeUtil.getMimeType(target.getFileName().toString());
        }
    }

    /**
     * 校验字符串根目录。
     *
     * @param basePath 根目录字符串
     * @return 已校验根目录字符串
     */
    private static String requireBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "local.base-path");
        }
        return basePath;
    }
}
