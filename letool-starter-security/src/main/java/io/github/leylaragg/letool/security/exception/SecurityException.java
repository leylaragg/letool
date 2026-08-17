package io.github.leylaragg.letool.security.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 安全模块配置和令牌基础设施初始化失败时抛出的统一异常。
 *
 * <p>异常只携带安全配置字段名，不包含密钥、令牌或用户身份数据。</p>
 */
public final class SecurityException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建安全模块统一异常。
     *
     * @param errorCode 安全模块稳定错误码
     * @param messageArgs 安全的消息模板参数
     */
    private SecurityException(SecurityErrorCode errorCode, Object[] messageArgs) {
        super(errorCode, messageArgs, null, null);
    }

    /**
     * 创建安全配置错误。
     *
     * @param field 不合法的安全配置字段名
     * @return 带配置错误码的异常
     * @throws IllegalArgumentException 当字段名为空白时抛出
     */
    public static SecurityException configurationInvalid(String field) {
        return new SecurityException(
                SecurityErrorCode.CONFIGURATION_INVALID,
                new Object[]{requireField(field)}
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
}
