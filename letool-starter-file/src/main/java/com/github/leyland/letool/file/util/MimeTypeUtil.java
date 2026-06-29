package com.github.leyland.letool.file.util;

import java.util.HashMap;
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
    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();

    static {
        // ---- 图片 ----
        EXT_TO_MIME.put("jpg", "image/jpeg");
        EXT_TO_MIME.put("jpeg", "image/jpeg");
        EXT_TO_MIME.put("png", "image/png");
        EXT_TO_MIME.put("gif", "image/gif");
        EXT_TO_MIME.put("bmp", "image/bmp");
        EXT_TO_MIME.put("svg", "image/svg+xml");
        EXT_TO_MIME.put("webp", "image/webp");

        // ---- 文档 ----
        EXT_TO_MIME.put("pdf", "application/pdf");
        EXT_TO_MIME.put("doc", "application/msword");
        EXT_TO_MIME.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXT_TO_MIME.put("xls", "application/vnd.ms-excel");
        EXT_TO_MIME.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXT_TO_MIME.put("ppt", "application/vnd.ms-powerpoint");
        EXT_TO_MIME.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // ---- 文本 ----
        EXT_TO_MIME.put("txt", "text/plain");
        EXT_TO_MIME.put("html", "text/html");
        EXT_TO_MIME.put("css", "text/css");
        EXT_TO_MIME.put("js", "application/javascript");
        EXT_TO_MIME.put("json", "application/json");
        EXT_TO_MIME.put("xml", "application/xml");

        // ---- 压缩 ----
        EXT_TO_MIME.put("zip", "application/zip");
        EXT_TO_MIME.put("rar", "application/x-rar-compressed");
        EXT_TO_MIME.put("gz", "application/gzip");
        EXT_TO_MIME.put("tar", "application/x-tar");

        // ---- 音视频 ----
        EXT_TO_MIME.put("mp3", "audio/mpeg");
        EXT_TO_MIME.put("mp4", "video/mp4");
        EXT_TO_MIME.put("avi", "video/x-msvideo");
        EXT_TO_MIME.put("mov", "video/quicktime");
    }

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
        return EXT_TO_MIME.getOrDefault(extension.toLowerCase(), "application/octet-stream");
    }
}
