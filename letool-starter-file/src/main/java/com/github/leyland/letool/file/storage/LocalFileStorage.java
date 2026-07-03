package com.github.leyland.letool.file.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 基于本地文件系统的 {@link FileStorageProvider} 实现。
 *
 * <p>所有文件操作都在 {@code basePath} 指定的根目录下进行，使用 Java NIO {@link java.nio.file.Files}
 * 和 {@link java.nio.file.Path} 完成文件的读写、删除和遍历。</p>
 *
 * <p><b>安全说明：</b>路径操作始终相对于 {@code basePath}，不会访问根目录以外的文件。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public class LocalFileStorage implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    /** 本地文件存储的根目录 */
    private final Path basePath;

    /**
     * 构造本地文件存储实例，并确保根目录存在。
     *
     * @param basePath 本地存储根目录的绝对路径，若目录不存在则自动创建
     * @throws UncheckedIOException 根目录创建失败时抛出
     */
    public LocalFileStorage(String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create base directory: " + basePath, e);
        }
    }

    // ===== 文件上传 =====

    /**
     * 将输入流写入本地文件系统。
     *
     * <p>目标目录不存在时会自动创建，若同名文件已存在则覆盖。</p>
     *
     * @param inputStream 文件内容的输入流
     * @param path        目标子目录（相对于 basePath）
     * @param fileName    目标文件名
     * @return 文件的绝对路径字符串
     * @throws UncheckedIOException 写入失败时抛出
     */
    @Override
    public String upload(InputStream inputStream, String path, String fileName) {
        try {
            Path dir = resolveStoragePath(path);
            Files.createDirectories(dir);
            Path filePath = ensureInsideBasePath(dir.resolve(fileName));
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("File uploaded: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file: " + path + "/" + fileName, e);
        }
    }

    // ===== 文件下载 =====

    /**
     * 从本地文件系统读取文件流。
     *
     * @param path 相对于 basePath 的文件路径
     * @return 文件内容的输入流，调用方负责关闭
     * @throws UncheckedIOException 文件读取失败时抛出
     */
    @Override
    public InputStream download(String path) {
        try {
            Path filePath = resolveStoragePath(path);
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download file: " + path, e);
        }
    }

    // ===== 文件删除 =====

    /**
     * 删除本地文件。
     *
     * @param path 相对于 basePath 的文件路径
     * @return {@code true} 删除成功，{@code false} 文件不存在
     * @throws UncheckedIOException 删除过程中发生 I/O 错误时抛出
     */
    @Override
    public boolean delete(String path) {
        try {
            Path filePath = resolveStoragePath(path);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + path, e);
        }
    }

    // ===== 存在性检查 =====

    /**
     * 检查文件是否存在于本地文件系统。
     *
     * @param path 相对于 basePath 的文件路径
     * @return {@code true} 文件存在，{@code false} 不存在
     */
    @Override
    public boolean exists(String path) {
        return Files.exists(resolveStoragePath(path));
    }

    // ===== 目录列表 =====

    /**
     * 列出本地目录下的所有文件和子目录。
     *
     * <p>遍历时跳过无法读取属性的文件（静默忽略）。若目录不存在则返回空列表。</p>
     *
     * @param path 相对于 basePath 的目录路径
     * @return 文件/目录信息列表
     * @throws UncheckedIOException 目录遍历失败时抛出
     */
    @Override
    public List<FileInfo> list(String path) {
        List<FileInfo> result = new ArrayList<>();
        Path dir = resolveStoragePath(path);
        if (!Files.exists(dir)) return result;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                    result.add(new FileInfo(
                            p.getFileName().toString(),
                            p.toString(),
                            attr.size(),
                            attr.isDirectory(),
                            attr.lastModifiedTime().toMillis()
                    ));
                } catch (IOException ignored) {
                    // 跳过无法读取属性的文件
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list directory: " + path, e);
        }
        return result;
    }

    /**
     * Resolves a user supplied storage path against the configured base directory.
     *
     * <p>Relative paths are resolved below {@code basePath}; absolute paths are accepted only
     * when they already point inside {@code basePath}. This keeps callers compatible with the
     * absolute path returned by {@link #upload(InputStream, String, String)} while still blocking
     * {@code ..} traversal and accidental writes outside the local storage root.</p>
     *
     * @param path relative storage key or absolute path returned by this storage provider
     * @return normalized absolute path inside {@code basePath}
     * @throws IllegalArgumentException when the resolved path escapes {@code basePath}
     */
    private Path resolveStoragePath(String path) {
        if (path == null || path.isBlank()) {
            return basePath;
        }
        Path inputPath = Paths.get(path);
        Path candidate = inputPath.isAbsolute() ? inputPath : basePath.resolve(inputPath);
        return ensureInsideBasePath(candidate);
    }

    /**
     * Normalizes a candidate path and verifies that it remains below the configured base path.
     *
     * @param candidate candidate path produced by a storage operation
     * @return normalized absolute path inside {@code basePath}
     * @throws IllegalArgumentException when the candidate points outside {@code basePath}
     */
    private Path ensureInsideBasePath(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(basePath)) {
            throw new IllegalArgumentException("Path escapes local storage base directory: " + candidate);
        }
        return normalized;
    }
}
