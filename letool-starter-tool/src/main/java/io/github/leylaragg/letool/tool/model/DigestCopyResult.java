package io.github.leylaragg.letool.tool.model;

/**
 * 流复制与 SHA-256 计算的不可变结果。
 *
 * @param bytesCopied 实际复制的字节数
 * @param sha256      小写十六进制 SHA-256 摘要
 */
public record DigestCopyResult(long bytesCopied, String sha256) {
}
