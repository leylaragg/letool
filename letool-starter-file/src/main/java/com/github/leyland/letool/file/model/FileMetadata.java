package com.github.leyland.letool.file.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 不包含文件内容的不可变元数据。
 *
 * @param key 文件或目录的逻辑键
 * @param name 文件或目录名称
 * @param size 文件大小；目录固定为 {@code 0}
 * @param directory 是否为目录
 * @param lastModified 最后修改时间
 * @param contentType 文件媒体类型
 */
public record FileMetadata(
        String key,
        String name,
        long size,
        boolean directory,
        Instant lastModified,
        String contentType) {

    /**
     * 校验元数据基础约束。
     *
     * @param key 文件或目录的逻辑键
     * @param name 文件或目录名称
     * @param size 文件大小
     * @param directory 是否为目录
     * @param lastModified 最后修改时间
     * @param contentType 文件媒体类型
     */
    public FileMetadata {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size 不能小于 0");
        }
        lastModified = Objects.requireNonNull(lastModified, "lastModified 不能为空");
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }
}
