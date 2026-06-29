package com.github.leyland.letool.file.util;

import java.util.UUID;

/**
 * 文件名工具类，提供文件扩展名提取、基础名获取、安全清洗、唯一名生成等功能。
 *
 * <p>所有方法均为静态方法，无需实例化。文件名处理统一转为小写以保证一致性。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class FileNameUtil {

    private FileNameUtil() {}

    // ===== 文件名生成 =====

    /**
     * 根据原始文件名生成 UUID 唯一文件名（保留原扩展名）。
     *
     * <p>生成规则：{@code UUID(32位去横线) + 原扩展名}。例如 {@code report.pdf} 生成类似
     * {@code a1b2c3d4e5f6789012345678901234ab.pdf} 的文件名。</p>
     *
     * <p>用途：上传文件时防止重名覆盖，同时保留扩展名以便后续 MIME 类型识别。</p>
     *
     * @param originalName 原始文件名（可能为 null）
     * @return 唯一文件名，若原始无扩展名则不包含点号
     */
    public static String generateUniqueName(String originalName) {
        String ext = getExtension(originalName);
        return UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
    }

    // ===== 扩展名处理 =====

    /**
     * 获取文件的扩展名（不含点号，小写）。
     *
     * @param fileName 文件名（可能包含路径）
     * @return 小写扩展名，若无扩展名或 fileName 为 null 则返回空字符串
     */
    public static String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 移除文件名的扩展名部分。
     *
     * <p>例如 {@code "photo.jpg"} 返回 {@code "photo"}，
     * {@code "archive.tar.gz"} 返回 {@code "archive.tar"}（只移除最后一级扩展名）。</p>
     *
     * @param fileName 文件名
     * @return 去掉扩展名的基础名，若 fileName 为 null 或无极扩展名则原样返回
     */
    public static String removeExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return fileName;
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    // ===== 文件名安全 =====

    /**
     * 清洗文件名中的非法字符，替换为下划线。
     *
     * <p>Windows 和 Linux 文件系统不允许的字符包括：{@code \ / : * ? " &lt; &gt; |}，
     * 这些字符会被替换为 {@code _}。同时去除头尾空格。</p>
     *
     * <p>用途：防止用户上传文件名包含非法字符导致存储失败。</p>
     *
     * @param fileName 原始文件名（可能为 null）
     * @return 安全文件名，null 输入返回 null
     */
    public static String sanitize(String fileName) {
        if (fileName == null) return null;
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
