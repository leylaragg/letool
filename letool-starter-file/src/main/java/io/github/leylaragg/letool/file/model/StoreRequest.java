package io.github.leylaragg.letool.file.model;

import java.util.Objects;

/**
 * 写入文件存储时使用的不可变请求。
 *
 * @param key 相对于存储根目录的逻辑键
 * @param declaredSize 声明的文件大小；未知时传 {@code -1}
 * @param originalName 已剥离客户端路径的原始文件名
 * @param contentType 文件媒体类型；未知时使用通用二进制类型
 * @param overwritePolicy 目标存在时的覆盖策略
 */
public record StoreRequest(
        String key,
        long declaredSize,
        String originalName,
        String contentType,
        OverwritePolicy overwritePolicy) {

    /**
     * 校验请求中与存储协议无关的基础约束。
     *
     * @param key 相对于存储根目录的逻辑键
     * @param declaredSize 声明的文件大小
     * @param originalName 已剥离客户端路径的原始文件名
     * @param contentType 文件媒体类型
     * @param overwritePolicy 目标存在时的覆盖策略
     */
    public StoreRequest {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (declaredSize < -1) {
            throw new IllegalArgumentException("declaredSize 不能小于 -1");
        }
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("originalName 不能为空");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        overwritePolicy = Objects.requireNonNull(overwritePolicy, "overwritePolicy 不能为空");
    }
}
