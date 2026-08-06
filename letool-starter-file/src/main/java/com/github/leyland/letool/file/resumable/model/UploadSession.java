package com.github.leyland.letool.file.resumable.model;

import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.transfer.TransferStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 可跨进程重建的断点续传会话快照。
 *
 * @param uploadId 上传会话编号
 * @param targetKey 创建时确定的最终存储逻辑键
 * @param originalName 已清理的原始文件名
 * @param contentType 规范化媒体类型
 * @param totalSize 完整文件字节数
 * @param confirmedOffset 已持久化并确认的下一分片偏移
 * @param expectedSha256 调用方声明的完整文件摘要
 * @param actualSha256 完成阶段计算的完整文件摘要
 * @param status 会话状态
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 * @param expiresAt 会话过期时间
 * @param version 乐观锁版本
 * @param storedFile 完成后的存储结果
 */
public record UploadSession(
        String uploadId,
        String targetKey,
        String originalName,
        String contentType,
        long totalSize,
        long confirmedOffset,
        String expectedSha256,
        String actualSha256,
        TransferStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        long version,
        StoredFile storedFile) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    /**
     * 校验会话中所有可持久化字段。
     *
     * @param uploadId 上传会话编号
     * @param targetKey 最终存储逻辑键
     * @param originalName 原始文件名
     * @param contentType 媒体类型
     * @param totalSize 完整文件字节数
     * @param confirmedOffset 已确认偏移
     * @param expectedSha256 声明的完整文件摘要
     * @param actualSha256 实际完整文件摘要
     * @param status 会话状态
     * @param createdAt 创建时间
     * @param updatedAt 最近更新时间
     * @param expiresAt 过期时间
     * @param version 乐观锁版本
     * @param storedFile 最终存储结果
     */
    public UploadSession {
        uploadId = requireUuid(uploadId);
        targetKey = requireText(targetKey, "targetKey");
        originalName = requireText(originalName, "originalName");
        contentType = requireText(contentType, "contentType");
        if (totalSize <= 0 || confirmedOffset < 0 || confirmedOffset > totalSize) {
            throw new IllegalArgumentException("会话字节边界不合法");
        }
        expectedSha256 = normalizeSha256(expectedSha256);
        actualSha256 = normalizeSha256(actualSha256);
        status = Objects.requireNonNull(status, "status 不能为空");
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (updatedAt.isBefore(createdAt) || expiresAt.isBefore(updatedAt) || version < 0) {
            throw new IllegalArgumentException("会话时间或版本不合法");
        }
        if (status == TransferStatus.COMPLETED && storedFile == null) {
            throw new IllegalArgumentException("已完成会话必须包含存储结果");
        }
    }

    /**
     * 创建推进偏移后的快照，版本由仓库保存时递增。
     *
     * @param offset 新可信偏移
     * @param nextStatus 新状态
     * @param nextExpiresAt 新过期时间
     * @return 更新后的会话快照
     */
    public UploadSession withProgress(
            long offset,
            TransferStatus nextStatus,
            Instant nextExpiresAt) {
        return withProgress(offset, nextStatus, updatedAt, nextExpiresAt);
    }

    /**
     * 创建推进偏移和更新时间后的快照，版本由仓库保存时递增。
     *
     * @param offset 新可信偏移
     * @param nextStatus 新状态
     * @param nextUpdatedAt 新更新时间
     * @param nextExpiresAt 新过期时间
     * @return 更新后的会话快照
     */
    public UploadSession withProgress(
            long offset,
            TransferStatus nextStatus,
            Instant nextUpdatedAt,
            Instant nextExpiresAt) {
        return new UploadSession(
                uploadId, targetKey, originalName, contentType, totalSize, offset,
                expectedSha256, actualSha256, nextStatus, createdAt, nextUpdatedAt,
                nextExpiresAt, version, storedFile);
    }

    /**
     * 创建状态、摘要和最终结果发生变化后的快照。
     *
     * @param nextStatus 新状态
     * @param nextActualSha256 新计算的完整摘要
     * @param nextStoredFile 新存储结果
     * @param nextUpdatedAt 更新时间
     * @param nextExpiresAt 过期时间
     * @return 更新后的会话快照
     */
    public UploadSession withState(
            TransferStatus nextStatus,
            String nextActualSha256,
            StoredFile nextStoredFile,
            Instant nextUpdatedAt,
            Instant nextExpiresAt) {
        return new UploadSession(
                uploadId, targetKey, originalName, contentType, totalSize,
                confirmedOffset, expectedSha256, nextActualSha256, nextStatus,
                createdAt, nextUpdatedAt, nextExpiresAt, version, nextStoredFile);
    }

    /**
     * 替换仓库递增后的乐观锁版本。
     *
     * @param nextVersion 新版本
     * @return 带新版本的会话快照
     */
    public UploadSession withVersion(long nextVersion) {
        return new UploadSession(
                uploadId, targetKey, originalName, contentType, totalSize,
                confirmedOffset, expectedSha256, actualSha256, status, createdAt,
                updatedAt, expiresAt, nextVersion, storedFile);
    }

    /**
     * 规范化 SHA-256 摘要。
     *
     * @param value 原始摘要
     * @return 小写摘要；未声明时为空
     */
    public static String normalizeSha256(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("SHA-256 摘要格式不合法");
        }
        return normalized;
    }

    /**
     * 校验上传编号必须是规范 UUID。
     *
     * @param value 上传编号
     * @return 规范化 UUID 字符串
     */
    private static String requireUuid(String value) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException("uploadId 必须是规范 UUID");
            }
            return normalized;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("uploadId 必须是规范 UUID", exception);
        }
    }

    /**
     * 校验必填文本。
     *
     * @param value 文本值
     * @param name 参数名
     * @return 原文本值
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
