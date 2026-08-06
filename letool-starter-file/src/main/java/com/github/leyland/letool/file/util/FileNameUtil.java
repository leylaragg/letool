package com.github.leyland.letool.file.util;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件名提取、清洗和唯一名称生成工具。
 */
public final class FileNameUtil {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNameUtil() {
    }

    /**
     * 生成保留原扩展名的 UUID 文件名。
     *
     * @param originalName 原始文件名
     * @return 唯一文件名
     */
    public static String generateUniqueName(String originalName) {
        String extension = getExtension(originalName);
        String identifier = UUID.randomUUID().toString().replace("-", "");
        return extension.isEmpty() ? identifier : identifier + "." + extension;
    }

    /**
     * 从浏览器上传的路径形式中提取最终文件名。
     *
     * @param originalName 客户端原始文件名
     * @return 不包含客户端目录的文件名；空值原样返回
     */
    public static String extractClientFileName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String normalized = originalName.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    /**
     * 获取小写扩展名，不包含点号。
     *
     * @param fileName 文件名或客户端路径
     * @return 小写扩展名；没有扩展名时返回空字符串
     */
    public static String getExtension(String fileName) {
        String name = extractClientFileName(fileName);
        if (name == null) {
            return "";
        }
        int index = name.lastIndexOf('.');
        if (index <= 0 || index == name.length() - 1) {
            return "";
        }
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 移除最后一级扩展名。
     *
     * @param fileName 文件名
     * @return 不包含最后一级扩展名的文件名
     */
    public static String removeExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? fileName : fileName.substring(0, index);
    }

    /**
     * 将控制字符和跨平台非法字符替换为下划线，并限制文件名长度。
     *
     * @param fileName 原始文件名
     * @return 安全文件名；空值返回空值
     */
    public static String sanitize(String fileName) {
        if (fileName == null) {
            return null;
        }
        String candidate = fileName.strip();
        StringBuilder builder = new StringBuilder(candidate.length());
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (character < 32 || character == 127 || "\\/:*?\"<>|".indexOf(character) >= 0) {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }
        String sanitized = builder.toString().trim();
        while (sanitized.endsWith(".") || sanitized.endsWith(" ")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.length() > MAX_FILE_NAME_LENGTH) {
            String extension = getExtension(sanitized);
            int suffixLength = extension.isEmpty() ? 0 : extension.length() + 1;
            int baseLength = Math.max(1, MAX_FILE_NAME_LENGTH - suffixLength);
            sanitized = sanitized.substring(0, baseLength)
                    + (extension.isEmpty() ? "" : "." + extension);
        }
        String baseName = removeExtension(sanitized).toUpperCase(Locale.ROOT);
        if (WINDOWS_RESERVED_NAMES.contains(baseName)) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }
}
