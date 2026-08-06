package com.github.leyland.letool.file.resumable.model;

import com.github.leyland.letool.file.transfer.TransferStatus;

/**
 * 分片成功落盘并持久化会话后的可信确认结果。
 *
 * @param uploadId 上传会话编号
 * @param confirmedOffset 已确认的下一分片起始偏移
 * @param totalSize 完整文件字节数
 * @param status 当前传输状态
 */
public record UploadProgress(
        String uploadId,
        long confirmedOffset,
        long totalSize,
        TransferStatus status) {
}
