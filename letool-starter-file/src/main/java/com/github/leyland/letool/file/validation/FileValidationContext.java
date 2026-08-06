package com.github.leyland.letool.file.validation;

import java.util.Arrays;

/**
 * 用户上传校验策略可以读取的不可变上下文。
 */
public final class FileValidationContext {

    private final String originalName;
    private final String safeName;
    private final String extension;
    private final String declaredContentType;
    private final long declaredSize;
    private final String detectedType;
    private final byte[] header;

    /**
     * 创建上传校验上下文。
     *
     * @param originalName 已剥离客户端路径的原始文件名
     * @param safeName 清洗后的安全文件名
     * @param extension 小写扩展名
     * @param declaredContentType 声明媒体类型
     * @param declaredSize 声明大小
     * @param detectedType 轻量探测类型
     * @param header 文件头副本
     */
    public FileValidationContext(
            String originalName,
            String safeName,
            String extension,
            String declaredContentType,
            long declaredSize,
            String detectedType,
            byte[] header) {
        this.originalName = originalName;
        this.safeName = safeName;
        this.extension = extension;
        this.declaredContentType = declaredContentType;
        this.declaredSize = declaredSize;
        this.detectedType = detectedType;
        this.header = header == null ? new byte[0] : Arrays.copyOf(header, header.length);
    }

    /** @return 已剥离客户端路径的原始文件名 */
    public String originalName() { return originalName; }

    /** @return 清洗后的安全文件名 */
    public String safeName() { return safeName; }

    /** @return 小写扩展名 */
    public String extension() { return extension; }

    /** @return 声明媒体类型 */
    public String declaredContentType() { return declaredContentType; }

    /** @return 声明大小 */
    public long declaredSize() { return declaredSize; }

    /** @return 轻量探测类型 */
    public String detectedType() { return detectedType; }

    /**
     * 获取防御性复制的文件头。
     *
     * @return 文件头副本
     */
    public byte[] header() { return Arrays.copyOf(header, header.length); }
}
