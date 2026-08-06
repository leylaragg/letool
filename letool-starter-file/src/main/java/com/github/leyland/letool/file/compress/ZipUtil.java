package com.github.leyland.letool.file.compress;

import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 基于 Apache Commons Compress 中央目录预检的 ZIP 便捷工具。
 */
public final class ZipUtil {

    private static final Logger log = LoggerFactory.getLogger(ZipUtil.class);
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:.*");

    private ZipUtil() {
    }

    /**
     * 压缩文件或目录，不保留源目录名称。
     *
     * @param sourcePath 源文件或目录
     * @param outputZip 输出 ZIP 文件
     */
    public static void compress(String sourcePath, String outputZip) {
        compress(Path.of(sourcePath), Path.of(outputZip), false);
    }

    /**
     * 压缩文件或目录。
     *
     * @param sourcePath 源文件或目录
     * @param outputZip 输出 ZIP 文件
     * @param includeRoot 是否保留源目录名称
     */
    public static void compress(String sourcePath, String outputZip, boolean includeRoot) {
        compress(Path.of(sourcePath), Path.of(outputZip), includeRoot);
    }

    /**
     * 压缩文件或目录，并通过同目录临时文件避免暴露半成品。
     *
     * @param sourcePath 源文件或目录
     * @param outputZip 输出 ZIP 文件
     * @param includeRoot 是否保留源目录名称
     */
    public static void compress(Path sourcePath, Path outputZip, boolean includeRoot) {
        if (sourcePath == null || outputZip == null) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "源路径和输出路径不能为空");
        }
        Path source = sourcePath.toAbsolutePath().normalize();
        Path output = outputZip.toAbsolutePath().normalize();
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "源路径不存在");
        }
        if (Files.isSymbolicLink(source)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "源路径不能是符号链接");
        }
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && output.startsWith(source)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "输出文件不能位于源目录内部");
        }

        Path parent = output.getParent();
        if (parent == null) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "输出目录不存在");
        }
        Path temporary = parent.resolve("." + output.getFileName() + "."
                + UUID.randomUUID() + ".tmp");
        try {
            verifyNoSymbolicLinkAncestors(parent);
            Files.createDirectories(parent);
            try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(temporary))) {
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    compressDirectory(source, outputStream, includeRoot);
                } else {
                    writeFileEntry(source, source.getFileName().toString(), outputStream);
                }
            }
            moveArchive(temporary, output);
        } catch (FileException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw FileException.causedBy(
                    FileErrorCode.ARCHIVE_OPERATION_FAILED, exception, "压缩失败");
        }
    }

    /**
     * 使用安全默认限制解压 ZIP。
     *
     * @param inputZip ZIP 文件
     * @param targetDirectory 目标目录
     */
    public static void decompress(String inputZip, String targetDirectory) {
        decompress(Path.of(inputZip), Path.of(targetDirectory), ZipLimits.defaults());
    }

    /**
     * 使用指定限制解压 ZIP。
     *
     * @param inputZip ZIP 文件
     * @param targetDirectory 目标目录
     * @param limits 安全限制
     */
    public static void decompress(Path inputZip, Path targetDirectory, ZipLimits limits) {
        if (inputZip == null || targetDirectory == null || limits == null) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "解压参数不能为空");
        }
        Path archive = inputZip.toAbsolutePath().normalize();
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 文件不存在");
        }
        verifyNoSymbolicLinkAncestors(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "解压目标必须是目录");
        }
        List<Path> createdPaths = new ArrayList<>();
        try (ZipFile zipFile = ZipFile.builder().setPath(archive).get()) {
            List<PreparedEntry> entries = preflight(zipFile, target, limits);
            createDirectory(target, createdPaths);
            long totalWritten = 0;
            for (PreparedEntry preparedEntry : entries) {
                ZipArchiveEntry entry = preparedEntry.entry();
                Path output = preparedEntry.output();
                if (entry.isDirectory()) {
                    createDirectory(output, createdPaths);
                    continue;
                }
                createDirectory(output.getParent(), createdPaths);
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    throw FileException.of(
                            FileErrorCode.ARCHIVE_OPERATION_FAILED, "目标文件已存在");
                }
                Files.createFile(output);
                createdPaths.add(output);
                try (InputStream inputStream = zipFile.getInputStream(entry);
                     var outputStream = Files.newOutputStream(output)) {
                    long entryWritten = 0;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (entryWritten + read > limits.maxEntrySize()
                                || totalWritten + read > limits.maxTotalSize()) {
                            throw FileException.of(
                                    FileErrorCode.ARCHIVE_OPERATION_FAILED, "解压实际大小超过限制");
                        }
                        outputStream.write(buffer, 0, read);
                        entryWritten += read;
                        totalWritten += read;
                    }
                }
            }
        } catch (FileException exception) {
            cleanupCreatedPaths(createdPaths);
            throw exception;
        } catch (IOException exception) {
            cleanupCreatedPaths(createdPaths);
            throw FileException.causedBy(
                    FileErrorCode.ARCHIVE_OPERATION_FAILED, exception, "解压失败");
        }
    }

    /**
     * 将单个字节数组压缩为 ZIP 字节数组。
     *
     * @param data 原始字节数组
     * @param entryName ZIP 条目名
     * @return ZIP 字节数组
     */
    public static byte[] compressToBytes(byte[] data, String entryName) {
        if (data == null || entryName == null || entryName.isBlank()) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "压缩参数不能为空");
        }
        String normalizedEntryName = normalizeEntryName(entryName);
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ZipOutputStream zipStream = new ZipOutputStream(byteStream)) {
            zipStream.putNextEntry(new ZipEntry(normalizedEntryName));
            zipStream.write(data);
            zipStream.closeEntry();
            zipStream.finish();
            return byteStream.toByteArray();
        } catch (IOException exception) {
            throw FileException.causedBy(
                    FileErrorCode.ARCHIVE_OPERATION_FAILED, exception, "内存压缩失败");
        }
    }

    /**
     * 预检 ZIP 中央目录。
     *
     * @param zipFile ZIP 文件
     * @param target 解压根目录
     * @param limits 安全限制
     * @return 已校验条目
     */
    private static List<PreparedEntry> preflight(
            ZipFile zipFile,
            Path target,
            ZipLimits limits) {
        List<PreparedEntry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long declaredTotal = 0;
        Enumeration<ZipArchiveEntry> enumeration = zipFile.getEntries();
        while (enumeration.hasMoreElements()) {
            ZipArchiveEntry entry = enumeration.nextElement();
            if (entries.size() >= limits.maxEntries()) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目数量超过限制");
            }
            String normalizedName = normalizeEntryName(entry.getName());
            if (!names.add(normalizedName.toLowerCase(Locale.ROOT))) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 包含重复条目");
            }
            if (entry.isUnixSymlink()) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 不允许符号链接条目");
            }
            long declaredSize = entry.getSize();
            if (declaredSize > limits.maxEntrySize()) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目声明大小超过限制");
            }
            if (declaredSize > 0) {
                if (declaredTotal > limits.maxTotalSize() - declaredSize) {
                    throw FileException.of(
                            FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 声明总大小超过限制");
                }
                declaredTotal += declaredSize;
            }
            Path output = target.resolve(normalizedName.replace('/', java.io.File.separatorChar)).normalize();
            if (!output.startsWith(target)) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目越过目标目录");
            }
            verifyNoSymbolicLink(target, output);
            entries.add(new PreparedEntry(entry, output));
        }
        return List.copyOf(entries);
    }

    /**
     * 递归压缩目录并拒绝符号链接。
     *
     * @param source 源目录
     * @param outputStream ZIP 输出流
     * @param includeRoot 是否保留根目录名
     * @throws IOException 文件遍历失败时抛出
     */
    private static void compressDirectory(
            Path source,
            ZipOutputStream outputStream,
            boolean includeRoot) throws IOException {
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("源目录包含符号链接");
                }
                if (!directory.equals(source)) {
                    String name = entryName(source, directory, includeRoot) + "/";
                    outputStream.putNextEntry(new ZipEntry(name));
                    outputStream.closeEntry();
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("源目录包含符号链接");
                }
                writeFileEntry(file, entryName(source, file, includeRoot), outputStream);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 写入单个 ZIP 文件条目。
     *
     * @param file 源文件
     * @param entryName ZIP 条目名
     * @param outputStream ZIP 输出流
     * @throws IOException 文件读取失败时抛出
     */
    private static void writeFileEntry(
            Path file,
            String entryName,
            ZipOutputStream outputStream) throws IOException {
        outputStream.putNextEntry(new ZipEntry(normalizeEntryName(entryName)));
        Files.copy(file, outputStream);
        outputStream.closeEntry();
    }

    /**
     * 计算源路径对应的 ZIP 条目名称。
     *
     * @param source 源根目录
     * @param path 当前路径
     * @param includeRoot 是否保留根目录名
     * @return ZIP 条目名称
     */
    private static String entryName(Path source, Path path, boolean includeRoot) {
        String relative = source.relativize(path).toString().replace('\\', '/');
        return includeRoot ? source.getFileName() + "/" + relative : relative;
    }

    /**
     * 校验并规范化 ZIP 条目名称。
     *
     * @param entryName 原始条目名
     * @return 使用斜杠分隔的安全相对名称
     */
    private static String normalizeEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目名为空");
        }
        String candidate = entryName.replace('\\', '/');
        if (candidate.indexOf('\0') >= 0
                || candidate.startsWith("/")
                || WINDOWS_ABSOLUTE_PATH.matcher(candidate).matches()) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目路径不安全");
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isBlank()) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目名为空");
        }
        Path normalized = Path.of(candidate).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (result.isBlank() || result.equals(".")) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目名为空");
        }
        if (result.equals("..") || result.startsWith("../")) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "ZIP 条目越过目标目录");
        }
        return result;
    }

    /**
     * 检查目标路径已有祖先中不存在符号链接。
     *
     * @param root 解压根目录
     * @param output 条目目标路径
     */
    private static void verifyNoSymbolicLink(Path root, Path output) {
        Path current = root;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
            throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "目标目录不能是符号链接");
        }
        for (Path segment : root.relativize(output)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw FileException.of(FileErrorCode.ARCHIVE_OPERATION_FAILED, "目标路径包含符号链接");
            }
        }
    }

    /**
     * 检查目标路径所有已存在的祖先，避免通过中间符号链接逃逸到其他目录。
     *
     * @param target 待创建或使用的目标路径
     */
    private static void verifyNoSymbolicLinkAncestors(Path target) {
        Path absolute = target.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current == null ? segment : current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw FileException.of(
                        FileErrorCode.ARCHIVE_OPERATION_FAILED, "目标路径包含符号链接");
            }
        }
    }

    /**
     * 创建目录并记录本次新建路径。
     *
     * @param directory 目标目录
     * @param createdPaths 本次创建路径集合
     * @throws IOException 目录创建失败时抛出
     */
    private static void createDirectory(Path directory, List<Path> createdPaths) throws IOException {
        if (directory == null || Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path parent = directory.getParent();
        if (parent != null) {
            createDirectory(parent, createdPaths);
        }
        Files.createDirectory(directory);
        createdPaths.add(directory);
    }

    /**
     * 清理本次解压创建的文件和空目录。
     *
     * @param createdPaths 本次创建路径集合
     */
    private static void cleanupCreatedPaths(List<Path> createdPaths) {
        createdPaths.stream()
                .sorted(Comparator.comparingInt((Path path) -> path.getNameCount()).reversed())
                .forEach(ZipUtil::deleteQuietly);
    }

    /**
     * 原子移动归档临时文件，平台不支持时使用同目录普通移动。
     *
     * @param temporary 临时 ZIP 文件
     * @param output 最终 ZIP 文件
     * @throws IOException 移动失败时抛出
     */
    private static void moveArchive(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 静默删除清理目标，主异常由调用方保留。
     *
     * @param path 待删除路径
     */
    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // 清理失败不覆盖原始归档异常，但保留可观测诊断信息。
            log.warn("ZIP 临时文件或解压产物清理失败：{}", path, exception);
        }
    }

    /**
     * 已通过中央目录预检的 ZIP 条目。
     *
     * @param entry ZIP 条目
     * @param output 解压目标路径
     */
    private record PreparedEntry(ZipArchiveEntry entry, Path output) {
    }
}
