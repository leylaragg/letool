package com.github.leyland.letool.file.util;

import java.util.Map;

/**
 * MIME 类型查询工具，根据文件扩展名返回对应的 MIME 类型字符串。
 *
 * <p>内置了常见的 28 种文件扩展名 -> MIME 类型的映射，覆盖图片、文档、压缩包、音视频等大类。
 * 未找到匹配的扩展名时返回 {@code "application/octet-stream"}（通用二进制流）。</p>
 *
 * <p>扩展名不区分大小写，建议通过 {@link FileNameUtil#getExtension(String)} 预处理。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class MimeTypeUtil {

    /**
     * 文件扩展名（小写） -> MIME 类型的映射表。
     */
    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
        // ---- 图片 ----
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("webp", "image/webp"),

        // ---- 文档 ----
        Map.entry("pdf", "application/pdf"),
        Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt", "application/vnd.ms-powerpoint"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),

        // ---- 文本 ----
        Map.entry("txt", "text/plain"),
        Map.entry("html", "text/html"),
        Map.entry("css", "text/css"),
        Map.entry("js", "application/javascript"),
        Map.entry("json", "application/json"),
        Map.entry("xml", "application/xml"),

        // ---- 压缩 ----
        Map.entry("zip", "application/zip"),
        Map.entry("rar", "application/x-rar-compressed"),
        Map.entry("gz", "application/gzip"),
        Map.entry("tar", "application/x-tar"),

        // ---- 音视频 ----
        Map.entry("mp3", "audio/mpeg"),
        Map.entry("mp4", "video/mp4"),
        Map.entry("avi", "video/x-msvideo"),
        Map.entry("mov", "video/quicktime")
    );

    private MimeTypeUtil() {}

    // ===== MIME 查询 =====

    /**
     * 根据文件名获取 MIME 类型。
     *
     * <p>内部会调用 {@link FileNameUtil#getExtension(String)} 提取扩展名。</p>
     *
     * @param fileName 文件名（含扩展名，如 "photo.jpg"）
     * @return MIME 类型字符串，如 "image/jpeg"；未知扩展名返回 "application/octet-stream"
     */
    public static String getMimeType(String fileName) {
        String ext = FileNameUtil.getExtension(fileName);
        return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * 根据扩展名字符串获取 MIME 类型（无需完整文件名）。
     *
     * <p>与 {@link #getMimeType(String)} 的区别在于此方法直接接收扩展名而非文件名。</p>
     *
     * @param extension 文件扩展名，带或不带点号均可，如 "jpg" 或 ".jpg"
     * @return MIME 类型字符串；null 或未知扩展名返回 "application/octet-stream"
     */
    public static String getMimeTypeByExt(String extension) {
        if (extension == null) return "application/octet-stream";
        // 去除前导点号，支持 ".jpg" 和 "jpg" 两种输入格式
        String normalized = extension.startsWith(".") ? extension.substring(1) : extension;
        return EXT_TO_MIME.getOrDefault(normalized.toLowerCase(), "application/octet-stream");
    }
}
