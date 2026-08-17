package io.github.leylaragg.letool.file.model;

/**
 * 文件存储实现可以显式声明的能力。
 */
public enum StorageCapability {

    /** 支持目录列举。 */
    DIRECTORY_LISTING,

    /** 支持同文件系统原子替换。 */
    ATOMIC_REPLACE,

    /** 支持按字节区间读取。 */
    RANGE_READ,

    /** 传输链路默认使用安全协议。 */
    SECURE_TRANSPORT
}
