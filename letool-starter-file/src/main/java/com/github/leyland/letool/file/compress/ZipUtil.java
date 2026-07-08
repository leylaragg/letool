package com.github.leyland.letool.file.compress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 压缩/解压工具类，提供目录压缩、解压及字节数组压缩功能。
 *
 * <p>基于 JDK 内置的 {@link java.util.zip.ZipOutputStream} 和 {@link java.util.zip.ZipInputStream}，
 * 无需引入第三方依赖。所有方法均为静态方法，工具类不可实例化。</p>
 *
 * <p><b>目录压缩：</b>使用 {@link Files#walkFileTree} 递归遍历目录树，
 * 将每个文件写入 ZIP 条目，并自动处理路径分隔符（统一为 {@code /}）。</p>
 *
 * <p><b>解压：</b>逐条读取 ZIP 条目，按条目名称还原目录结构和文件。</p>
 *
 * <p><b>字节数组压缩：</b>将内存中的字节数据直接写入 ZIP 流，适用于生成下载的压缩包。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class ZipUtil {

    private static final Logger log = LoggerFactory.getLogger(ZipUtil.class);

    private ZipUtil() {}

    // ===== 目录/文件压缩 =====

    /**
     * 将目录或文件压缩为 ZIP 包（不保留根目录），相当于调用 {@code compress(sourceDir, outputZip, false)}。
     *
     * @param sourceDir 待压缩的源目录或文件路径
     * @param outputZip 输出的 ZIP 文件路径
     * @throws RuntimeException       源目录不存在时抛出
     * @throws UncheckedIOException   压缩过程中发生 I/O 错误时抛出
     */
    public static void compress(String sourceDir, String outputZip) {
        compress(sourceDir, outputZip, false);
    }

    /**
     * 将目录或文件压缩为 ZIP 包。
     *
     * <p>若源路径为目录，使用 {@code Files.walkFileTree} 递归遍历；若为文件，直接添加单个条目。</p>
     *
     * <p>{@code includeRoot} 参数控制 ZIP 中的条目路径是否包含源目录的父目录名：
     * <ul>
     *   <li>{@code false}（默认）— 条目路径相对于源目录，解压后直接得到目录内容</li>
     *   <li>{@code true} — 条目路径包含源目录的父目录名，解压后多一层父目录</li>
     * </ul>
     *
     * @param sourceDir   待压缩的源目录或文件路径
     * @param outputZip   输出的 ZIP 文件路径
     * @param includeRoot 是否在 ZIP 中保留源目录的父目录名
     * @throws RuntimeException     源目录不存在时抛出
     * @throws UncheckedIOException 压缩过程中发生 I/O 错误时抛出
     */
    public static void compress(String sourceDir, String outputZip, boolean includeRoot) {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            throw new RuntimeException("Source directory does not exist: " + sourceDir);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(outputZip)))) {
            if (Files.isDirectory(sourcePath)) {
                // 递归遍历目录，每个文件作为一个 ZIP 条目
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName;
                        if (includeRoot) {
                            Path parent = sourcePath.getParent();
                            String rootDirName = parent != null ? parent.getFileName().toString() : sourcePath.getFileName().toString();
                            entryName = rootDirName + "/"
                                    + sourcePath.relativize(file).toString().replace("\\", "/");
                        } else {
                            entryName = sourcePath.relativize(file).toString().replace("\\", "/");
                        }
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                // 单个文件直接添加
                zos.putNextEntry(new ZipEntry(sourcePath.getFileName().toString()));
                Files.copy(sourcePath, zos);
                zos.closeEntry();
            }
            log.debug("Compressed {} -> {}", sourceDir, outputZip);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress: " + sourceDir, e);
        }
    }

    // ===== ZIP 解压 =====

    /**
     * 将 ZIP 文件解压到指定目录。
     *
     * <p>自动创建子目录结构，若目标文件已存在则覆盖。支持包含空目录条目的 ZIP 包。</p>
     *
     * @param inputZip  ZIP 文件路径
     * @param targetDir 解压目标目录，不存在时自动创建
     * @throws UncheckedIOException 解压过程中发生 I/O 错误时抛出
     */
    public static void decompress(String inputZip, String targetDir) {
        Path targetPath = Paths.get(targetDir).toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(inputZip)))) {
            Files.createDirectories(targetPath);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = resolveZipEntry(targetPath, entry.getName());
                if (entry.isDirectory()) {
                    // 目录条目 — 仅创建目录
                    Files.createDirectories(filePath);
                } else {
                    // 文件条目 — 确保父目录存在后写入文件内容
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
            log.debug("Decompressed {} -> {}", inputZip, targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decompress: " + inputZip, e);
        }
    }

    /**
     * Resolves a ZIP entry below the extraction target and rejects path traversal entries.
     *
     * <p>ZIP files may contain entries such as {@code ../evil.txt} or absolute paths. Normalizing
     * and checking the resolved path before writing prevents those entries from escaping the target
     * directory during extraction.</p>
     *
     * @param targetPath normalized extraction root
     * @param entryName  entry name read from the ZIP archive
     * @return normalized path for the entry inside {@code targetPath}
     * @throws IllegalArgumentException when the entry points outside {@code targetPath}
     */
    private static Path resolveZipEntry(Path targetPath, String entryName) {
        Path filePath = targetPath.resolve(entryName).normalize();
        if (!filePath.startsWith(targetPath)) {
            throw new IllegalArgumentException("ZIP entry escapes target directory: " + entryName);
        }
        return filePath;
    }

    // ===== 内存字节压缩 =====

    /**
     * 将字节数组以指定条目名压缩为 ZIP 字节数组。
     *
     * <p>适用于在内存中动态生成 ZIP 包（如导出多个文件为一个压缩包）的场景。
     * 返回的字节数组可直接写入 HTTP 响应输出流供用户下载。</p>
     *
     * @param data      待压缩的原始数据
     * @param entryName ZIP 中的条目名（文件名）
     * @return 压缩后的 ZIP 字节数组
     * @throws UncheckedIOException 压缩过程中发生 I/O 错误时抛出
     */
    public static byte[] compressToBytes(byte[] data, String entryName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data);
            zos.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress bytes", e);
        }
        return baos.toByteArray();
    }
}
