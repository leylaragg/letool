package io.github.leylaragg.letool.cipher.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;
import java.util.Objects;

/**
 * 加密模块统一系统异常。
 *
 * <p>异常只记录算法名称和安全原因，不会记录密钥、明文、密文或签名原值。</p>
 */
public final class CipherException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建加密模块异常。
     *
     * @param errorCode 稳定错误码
     * @param messageArgs 消息模板参数
     * @param cause 底层原因，无法取得时可为 {@code null}
     */
    private CipherException(CipherErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建参数无效异常。
     *
     * @param reason 不含敏感数据的原因
     * @return 参数无效异常
     */
    public static CipherException invalidParameter(String reason) {
        return new CipherException(CipherErrorCode.INVALID_PARAMETER, new Object[]{safeReason(reason)}, null);
    }

    /**
     * 创建密钥无效异常。
     *
     * @param algorithm 算法或密钥类型，不得包含密钥原值
     * @return 密钥无效异常
     */
    public static CipherException invalidKey(String algorithm) {
        return new CipherException(CipherErrorCode.INVALID_KEY, new Object[]{safeReason(algorithm)}, null);
    }

    /**
     * 创建密文封装无效异常。
     *
     * @param reason 不含密文原值的原因
     * @return 密文封装无效异常
     */
    public static CipherException invalidEnvelope(String reason) {
        return new CipherException(CipherErrorCode.INVALID_ENVELOPE, new Object[]{safeReason(reason)}, null);
    }

    /**
     * 创建加密执行失败异常。
     *
     * @param algorithm 算法名称
     * @param cause 底层异常
     * @return 加密执行失败异常
     */
    public static CipherException encryptionFailed(String algorithm, Throwable cause) {
        return new CipherException(
                CipherErrorCode.ENCRYPTION_FAILED,
                new Object[]{safeReason(algorithm)},
                Objects.requireNonNull(cause, "cause must not be null"));
    }

    /**
     * 创建解密执行失败异常。
     *
     * @param algorithm 算法名称
     * @param cause 底层异常
     * @return 解密执行失败异常
     */
    public static CipherException decryptionFailed(String algorithm, Throwable cause) {
        return new CipherException(
                CipherErrorCode.DECRYPTION_FAILED,
                new Object[]{safeReason(algorithm)},
                Objects.requireNonNull(cause, "cause must not be null"));
    }

    /**
     * 创建其他密码运算失败异常。
     *
     * @param operation 运算名称
     * @param cause 底层异常
     * @return 密码运算失败异常
     */
    public static CipherException operationFailed(String operation, Throwable cause) {
        return new CipherException(
                CipherErrorCode.OPERATION_FAILED,
                new Object[]{safeReason(operation)},
                Objects.requireNonNull(cause, "cause must not be null"));
    }

    /**
     * 校验异常原因文本，避免生成不可诊断的空消息。
     *
     * @param reason 原因或算法名称
     * @return 去除首尾空白后的文本
     */
    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return reason.trim();
    }
}
