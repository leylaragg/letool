package com.github.leyland.letool.file.transfer;

/**
 * 文件模块可跟踪的传输类型。
 */
public enum TransferType {

    /** 普通上传。 */
    UPLOAD,

    /** 普通下载。 */
    DOWNLOAD,

    /** HTTP 单区间下载。 */
    RANGE_DOWNLOAD,

    /** 可恢复的连续分片上传。 */
    RESUMABLE_UPLOAD
}
