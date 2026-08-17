package io.github.leylaragg.letool.cache.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 缓存配置、实例查找和业务回源失败时抛出的统一异常。
 *
 * <p>异常消息只包含稳定错误码和安全字段名，不会拼接业务缓存名称、
 * 缓存 key、缓存 value 或底层异常文本。底层原因保留在异常链中，
 * 供受控日志和诊断使用。</p>
 */
public final class CacheException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建缓存模块统一异常。
     *
     * @param errorCode 缓存稳定错误码
     * @param messageArgs 安全的消息模板参数
     * @param cause 底层异常；没有底层异常时允许为 {@code null}
     */
    private CacheException(
            CacheErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建缓存配置错误。
     *
     * @param field 不合法的安全配置字段名
     * @return 带配置错误码的异常
     * @throws IllegalArgumentException 当字段名为空白时抛出
     */
    public static CacheException configurationInvalid(String field) {
        return new CacheException(
                CacheErrorCode.CONFIGURATION_INVALID,
                new Object[]{requireField(field)},
                null
        );
    }

    /**
     * 创建缓存实例不存在错误。
     *
     * @return 不包含业务缓存名称的异常
     */
    public static CacheException cacheNotFound() {
        return new CacheException(
                CacheErrorCode.CACHE_NOT_FOUND,
                null,
                null
        );
    }

    /**
     * 创建缓存回源失败异常。
     *
     * @param cause 业务回源抛出的原始异常
     * @return 保留原始原因链的安全异常
     * @throws IllegalArgumentException 当原因为 {@code null} 时抛出
     */
    public static CacheException loaderFailed(Throwable cause) {
        return new CacheException(
                CacheErrorCode.LOADER_FAILED,
                null,
                requireCause(cause)
        );
    }

    /**
     * 创建缓存失效消息格式错误。
     *
     * @return 失效消息错误
     */
    public static CacheException invalidationMessageInvalid() {
        return new CacheException(
                CacheErrorCode.INVALIDATION_MESSAGE_INVALID,
                null,
                null
        );
    }

    /**
     * 创建缓存名称与数据结构类型冲突错误。
     *
     * @return 不包含业务缓存名称的类型冲突异常
     */
    public static CacheException cacheTypeConflict() {
        return new CacheException(
                CacheErrorCode.CACHE_TYPE_CONFLICT,
                null,
                null
        );
    }

    /**
     * 校验可以安全公开的配置字段名。
     *
     * @param field 待校验字段名
     * @return 已校验字段名
     */
    private static String requireField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        return field;
    }

    /**
     * 校验需要保留的底层异常。
     *
     * @param cause 底层异常
     * @return 已校验异常
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
