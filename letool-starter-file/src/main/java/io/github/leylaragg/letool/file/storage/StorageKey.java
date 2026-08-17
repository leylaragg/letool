package io.github.leylaragg.letool.file.storage;

import io.github.leylaragg.letool.file.exception.FileErrorCode;
import io.github.leylaragg.letool.file.exception.FileException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 存储实现共享的逻辑键规范化工具。
 */
public final class StorageKey {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:.*");

    private StorageKey() {
    }

    /**
     * 规范化文件逻辑键。
     *
     * @param key 待校验逻辑键
     * @return 使用斜杠分隔的安全相对键
     */
    public static String file(String key) {
        return normalize(key, false);
    }

    /**
     * 规范化目录逻辑键。
     *
     * @param key 待校验目录键；空值表示根目录
     * @return 使用斜杠分隔的安全相对目录键
     */
    public static String directory(String key) {
        return normalize(key, true);
    }

    /**
     * 拼接目录和文件名并生成安全文件键。
     *
     * @param directory 目录逻辑键
     * @param fileName 文件名
     * @return 拼接后的文件逻辑键
     */
    public static String join(String directory, String fileName) {
        String normalizedDirectory = directory(directory);
        String normalizedFileName = file(fileName);
        if (normalizedFileName.contains("/")) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        return normalizedDirectory.isEmpty()
                ? normalizedFileName
                : normalizedDirectory + "/" + normalizedFileName;
    }

    /**
     * 将逻辑键转换为安全的协议内部格式。
     *
     * @param value 原始逻辑键
     * @param allowEmpty 是否允许根目录空键
     * @return 规范化逻辑键
     */
    private static String normalize(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) {
                return "";
            }
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        String candidate = value.trim().replace('\\', '/');
        if (candidate.indexOf('\0') >= 0
                || candidate.startsWith("/")
                || WINDOWS_ABSOLUTE_PATH.matcher(candidate).matches()) {
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isEmpty()) {
            if (allowEmpty) {
                return "";
            }
            throw FileException.of(FileErrorCode.UNSAFE_PATH);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : candidate.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw FileException.of(FileErrorCode.UNSAFE_PATH);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }
}
