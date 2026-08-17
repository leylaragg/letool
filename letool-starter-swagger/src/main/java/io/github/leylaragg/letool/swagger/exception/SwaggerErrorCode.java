package io.github.leylaragg.letool.swagger.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * Swagger 模块稳定错误码。
 *
 * <p>错误码用于统一日志、异常响应和程序判断，默认消息不依赖 Spring 上下文。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum SwaggerErrorCode implements ErrorCode {

    /** Swagger 配置不合法。 */
    CONFIGURATION_INVALID(
            "SWAGGER_CONFIGURATION_INVALID",
            "Swagger 配置不合法：{0}");

    /** 稳定的外部错误码标识。 */
    private final String code;

    /** 不依赖 Spring 上下文的默认中文消息模板。 */
    private final String defaultMessage;

    /**
     * 创建 Swagger 错误码。
     *
     * @param code 稳定错误码标识
     * @param defaultMessage 默认中文消息模板
     */
    SwaggerErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码标识。
     *
     * @return 稳定错误码标识
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认中文消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
