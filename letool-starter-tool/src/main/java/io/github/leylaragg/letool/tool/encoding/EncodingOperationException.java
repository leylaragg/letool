package io.github.leylaragg.letool.tool.encoding;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * Base64 或十六进制编解码失败时抛出的统一异常。
 *
 * <p>公开消息只包含编码类型或参数名称，不包含待编码、待解码的原始内容；
 * 底层失败原因保留在异常链中，便于受控诊断。</p>
 */
public final class EncodingOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建编码工具统一异常。
     *
     * @param errorCode 编码稳定错误码
     * @param safeSubject 安全的参数名称或编码类型
     * @param cause 底层失败原因
     */
    private EncodingOperationException(
            EncodingErrorCode errorCode,
            String safeSubject,
            Throwable cause) {
        super(errorCode, new Object[]{safe(safeSubject)}, null, cause);
    }

    /**
     * 创建编码参数不符合契约异常。
     *
     * @param parameterName 安全的参数名称
     * @return 参数异常
     */
    public static EncodingOperationException invalidArgument(String parameterName) {
        String safeName = safe(parameterName);
        return new EncodingOperationException(
                EncodingErrorCode.INVALID_ARGUMENT,
                safeName,
                new IllegalArgumentException("Invalid encoding argument: " + safeName)
        );
    }

    /**
     * 创建 Base64 解码失败异常。
     *
     * @param encodingType 安全的 Base64 编码类型
     * @param cause JDK Base64 解码异常
     * @return Base64 解码异常
     */
    public static EncodingOperationException base64DecodeFailed(
            String encodingType,
            Throwable cause) {
        return new EncodingOperationException(
                EncodingErrorCode.BASE64_DECODE_FAILED,
                encodingType,
                requireCause(cause)
        );
    }

    /**
     * 创建十六进制解码失败异常。
     *
     * @param cause 十六进制长度或字符校验异常
     * @return 十六进制解码异常
     */
    public static EncodingOperationException hexDecodeFailed(Throwable cause) {
        return new EncodingOperationException(
                EncodingErrorCode.HEX_DECODE_FAILED,
                "hex",
                requireCause(cause)
        );
    }

    /**
     * 校验必须保留的底层异常原因。
     *
     * @param cause 底层异常
     * @return 校验通过的异常
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }

    /**
     * 规范化公开消息中的安全标识。
     *
     * @param value 待规范化标识
     * @return 非空且非空白的安全标识
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
