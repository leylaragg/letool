package com.github.leyland.letool.print.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可扩展的打印产物格式及其基础媒体信息。
 *
 * <p>该值对象不把框架限制在内置格式；新增格式可以由渲染器或独立打印管线声明。</p>
 *
 * @author leyland
 */
public final class OutputFormat {

    /** 规范化后的输出格式标识。 */
    private final String value;

    /** 规范化后的 MIME 类型。 */
    private final String mediaType;

    /** 不含前导点的规范化文件扩展名。 */
    private final String fileExtension;

    /** 允许的格式标识模式。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /** 允许的文件扩展名模式。 */
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,16}");

    /** PDF 输出格式。 */
    public static final OutputFormat PDF = new OutputFormat("pdf", "application/pdf", "pdf");

    /** Office Open XML Word 输出格式。 */
    public static final OutputFormat DOCX = new OutputFormat(
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx");

    /**
     * 创建并规范化输出格式。
     *
     * @param value 输出格式标识
     * @param mediaType MIME 类型
     * @param fileExtension 建议文件扩展名，可包含一个前导点
     * @throws IllegalArgumentException 任一参数为空白或格式不安全时抛出
     */
    public OutputFormat(String value, String mediaType, String fileExtension) {
        this.value = normalizeIdentifier(value);
        this.mediaType = normalizeMediaType(mediaType);
        this.fileExtension = normalizeExtension(fileExtension);
    }

    /** @return 规范化后的输出格式标识 */
    public String value() {
        return value;
    }

    /** @return 规范化后的 MIME 类型 */
    public String mediaType() {
        return mediaType;
    }

    /** @return 不含前导点的文件扩展名 */
    public String fileExtension() {
        return fileExtension;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof OutputFormat that)) {
            return false;
        }
        return value.equals(that.value)
                && mediaType.equals(that.mediaType)
                && fileExtension.equals(that.fileExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mediaType, fileExtension);
    }

    @Override
    public String toString() {
        return "OutputFormat[value=" + value
                + ", mediaType=" + mediaType
                + ", fileExtension=" + fileExtension + "]";
    }

    /** 规范化输出格式标识。 */
    private static String normalizeIdentifier(String value) {
        if (value == null) {
            throw new IllegalArgumentException("outputFormat 不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("outputFormat 格式不合法：" + normalized);
        }
        return normalized;
    }

    /** 规范化 MIME 类型。 */
    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType 不能为空");
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            throw new IllegalArgumentException("mediaType 格式不合法：" + normalized);
        }
        return normalized;
    }

    /** 规范化建议文件扩展名。 */
    private static String normalizeExtension(String fileExtension) {
        if (fileExtension == null) {
            throw new IllegalArgumentException("fileExtension 不能为空");
        }
        String normalized = fileExtension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (!EXTENSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("fileExtension 格式不合法：" + normalized);
        }
        return normalized;
    }
}
