package io.github.leylaragg.letool.security.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 安全模块对外暴露的稳定错误码。
 */
public enum SecurityErrorCode implements ErrorCode {

    /** JWT 密钥、签发者、有效期或 CORS 参数不合法。 */
    CONFIGURATION_INVALID("SECURITY_001", "安全配置不合法：{0}"),

    /** 请求没有携带有效的访问令牌。 */
    UNAUTHENTICATED("SECURITY_002", "认证失败，请重新登录"),

    /** 当前用户没有访问目标资源所需的权限。 */
    ACCESS_DENIED("SECURITY_003", "权限不足");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息模板。 */
    private final String defaultMessage;

    /**
     * 创建安全模块错误码。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    SecurityErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认错误消息模板。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
