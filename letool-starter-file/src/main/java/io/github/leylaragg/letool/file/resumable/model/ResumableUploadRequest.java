package io.github.leylaragg.letool.file.resumable.model;

/**
 * 创建断点续传会话所需的不可变参数。
 *
 * @param directory 目标逻辑目录
 * @param originalName 原始文件名
 * @param contentType 声明媒体类型
 * @param totalSize 完整文件字节数
 * @param finalSha256 可选的完整文件 SHA-256 十六进制摘要
 */
public record ResumableUploadRequest(
        String directory,
        String originalName,
        String contentType,
        long totalSize,
        String finalSha256) {
}
