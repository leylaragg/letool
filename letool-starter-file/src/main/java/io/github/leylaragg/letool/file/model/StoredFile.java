package io.github.leylaragg.letool.file.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 文件成功写入存储后的不可变结果。
 *
 * @param key 可供后续读取、删除和查询的逻辑键
 * @param originalName 已剥离客户端路径的原始文件名
 * @param storedName 实际存储文件名
 * @param size 实际写入字节数
 * @param contentType 文件媒体类型
 * @param sha256 文件内容的 SHA-256 十六进制摘要；底层无法计算时允许为空
 * @param lastModified 存储完成时间
 */
public record StoredFile(
        String key,
        String originalName,
        String storedName,
        long size,
        String contentType,
        String sha256,
        Instant lastModified) {

    /**
     * 校验上传结果必须具备的稳定字段。
     *
     * @param key 文件逻辑键
     * @param originalName 已剥离客户端路径的原始文件名
     * @param storedName 实际存储文件名
     * @param size 实际写入字节数
     * @param contentType 文件媒体类型
     * @param sha256 SHA-256 十六进制摘要
     * @param lastModified 存储完成时间
     */
    public StoredFile {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("originalName 不能为空");
        }
        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("storedName 不能为空");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size 不能小于 0");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        lastModified = Objects.requireNonNull(lastModified, "lastModified 不能为空");
    }
}
