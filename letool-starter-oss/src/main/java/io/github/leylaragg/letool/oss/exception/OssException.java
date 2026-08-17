package io.github.leylaragg.letool.oss.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * OSS 模块统一系统异常。
 *
 * <p>异常保留稳定错误码、诊断参数和官方 SDK 的原始原因链，不在响应消息中拼接密钥、
 * 签名或厂商原始报文。</p>
 */
public final class OssException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 OSS 异常。
     *
     * @param errorCode OSS 错误码
     * @param messageArgs 默认消息模板参数
     * @param cause 底层原因；没有时可为 {@code null}
     */
    private OssException(OssErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的 OSS 异常。
     *
     * @param errorCode OSS 错误码
     * @param messageArgs 默认消息模板参数
     * @return 结构化 OSS 异常
     */
    public static OssException of(OssErrorCode errorCode, Object... messageArgs) {
        return new OssException(errorCode, messageArgs, null);
    }

    /**
     * 创建保留底层原因链的 OSS 异常。
     *
     * @param errorCode OSS 错误码
     * @param cause 非空底层异常
     * @param messageArgs 默认消息模板参数
     * @return 保留原因链的结构化 OSS 异常
     * @throws IllegalArgumentException 底层异常为空时抛出
     */
    public static OssException causedBy(
            OssErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new OssException(errorCode, messageArgs, cause);
    }
}
