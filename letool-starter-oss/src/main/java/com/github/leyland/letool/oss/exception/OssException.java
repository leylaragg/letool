package com.github.leyland.letool.oss.exception;

/**
 * OSS 对象存储模块的通用运行时异常。
 *
 * <p>该异常用于统一包裹各对象存储提供商（阿里云 OSS、MinIO、腾讯云 COS）操作过程中
 * 发生的所有异常，包括网络错误、权限不足、参数校验失败等。上层调用方可通过捕获
 * 此单一异常类型来处理所有 OSS 相关的错误场景，而无需关心底层提供商的具体异常类型。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * try {
 *     ossTemplate.upload("photos/1.jpg", inputStream);
 * } catch (OssException e) {
 *     log.error("OSS upload failed: {}", e.getMessage(), e);
 *     // 统一错误处理逻辑
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class OssException extends RuntimeException {

    // ======================== 构造方法 ========================

    /**
     * 使用错误消息构造异常。
     *
     * @param message 错误描述信息
     */
    public OssException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和导致异常的根因构造异常。
     *
     * @param message 错误描述信息
     * @param cause   导致异常的原始 Throwable
     */
    public OssException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 仅使用根因构造异常，消息由根因的 {@code toString()} 生成。
     *
     * @param cause 导致异常的原始 Throwable
     */
    public OssException(Throwable cause) {
        super(cause);
    }
}
