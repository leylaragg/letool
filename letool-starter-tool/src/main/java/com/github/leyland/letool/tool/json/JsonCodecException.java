package com.github.leyland.letool.tool.json;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;
import java.lang.reflect.Type;

/**
 * {@link JsonCodec} 抛出的基础设施异常。
 *
 * <p>异常保留稳定的 Letool 错误码和原始异常链。异常消息不会包含 JSON 原始内容，
 * 因为其中可能存在凭据或其他敏感业务数据。</p>
 */
public final class JsonCodecException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 使用安全的消息参数创建 JSON 基础设施异常。
     *
     * @param errorCode 稳定的 JSON 错误码
     * @param messageArgs 安全的消息模板参数，允许为 {@code null}
     * @param cause 已校验的底层实现异常
     */
    private JsonCodecException(JsonErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建序列化失败异常。
     *
     * @param cause 底层 JSON 实现异常，不允许为 {@code null}
     * @return 包含序列化错误码和原始原因的异常
     * @throws IllegalArgumentException {@code cause} 为 {@code null} 时抛出
     */
    public static JsonCodecException serializationFailed(Throwable cause) {
        return new JsonCodecException(
                JsonErrorCode.SERIALIZATION_FAILED,
                null,
                requireCause(cause)
        );
    }

    /**
     * 创建反序列化失败异常。
     *
     * @param targetType 指定的 Java 目标类型，不允许为 {@code null}
     * @param cause 底层 JSON 实现异常，不允许为 {@code null}
     * @return 包含安全目标类型描述和原始原因的异常
     * @throws IllegalArgumentException {@code targetType} 或 {@code cause} 为 {@code null} 时抛出
     */
    public static JsonCodecException deserializationFailed(Type targetType, Throwable cause) {
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        return new JsonCodecException(
                JsonErrorCode.DESERIALIZATION_FAILED,
                new Object[]{targetType.getTypeName()},
                requireCause(cause)
        );
    }

    /**
     * 校验需要保留的底层异常原因。
     *
     * @param cause 底层实现异常
     * @return 校验后的异常原因
     * @throws IllegalArgumentException {@code cause} 为 {@code null} 时抛出
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
