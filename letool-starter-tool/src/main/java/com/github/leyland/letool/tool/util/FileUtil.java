package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.function.IoConsumer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * 文件安全操作工具，提供根目录约束和临时文件原子写入能力。
 */
public final class FileUtil {

    /** 禁止实例化工具类。 */
    private FileUtil() {
    }

    /**
     * 将相对路径安全解析到指定根目录内。
     *
     * @param root         根目录
     * @param relativePath 相对路径文本
     * @return 规范化后的绝对目标路径
     * @throws IllegalArgumentException 路径为空、为绝对路径或越过根目录时抛出
     * @throws IOException 根目录无效或现有路径段包含符号链接时抛出
     */
    public static Path resolveUnderRoot(Path root, String relativePath) throws IOException {
        Objects.requireNonNull(root, "root 不能为空");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath 不能为空");
        }
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("relativePath 必须是相对路径");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize().toRealPath();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("root 必须是目录");
        }
        Path target = normalizedRoot.resolve(relative).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("relativePath 不能越过根目录");
        }
        Path current = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) {
                throw new IOException("relativePath 不能包含符号链接路径段");
            }
        }
        return target;
    }

    /**
     * 通过同目录临时文件安全写入目标文件。
     *
     * <p>允许替换时，写入成功后优先执行原子移动，文件系统不支持时降级为普通替换移动；
     * 禁止替换时使用普通非覆盖移动，以确保不会覆盖已有目标。
     * 写入或移动失败会保留原目标文件并清理临时文件。</p>
     *
     * @param target      目标文件
     * @param replace     目标存在时是否允许替换
     * @param writer      临时文件内容写入器
     * @throws IOException 创建目录、写入、移动或清理失败时抛出
     */
    public static void writeAtomically(
            Path target,
            boolean replace,
            IoConsumer<OutputStream> writer) throws IOException {
        Objects.requireNonNull(target, "target 不能为空");
        Objects.requireNonNull(writer, "writer 不能为空");
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("target 必须具有父目录");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".letool-", ".tmp");
        Throwable failure = null;
        try {
            try (OutputStream output = Files.newOutputStream(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.accept(output);
            }
            moveIntoPlace(temporary, normalizedTarget, replace);
            temporary = null;
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupException) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupException);
                    } else {
                        throw cleanupException;
                    }
                }
            }
        }
    }

    /**
     * 将临时文件移动到最终位置。
     *
     * @param temporary 临时文件
     * @param target    目标文件
     * @param replace   是否替换已有目标
     * @throws IOException 移动失败时抛出
     */
    private static void moveIntoPlace(Path temporary, Path target, boolean replace)
            throws IOException {
        if (!replace) {
            Files.move(temporary, target);
            return;
        }
        CopyOption[] atomicOptions = {
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        };
        try {
            Files.move(temporary, target, atomicOptions);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
