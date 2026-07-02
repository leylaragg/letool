package com.github.leyland.letool.ai.exception;

/**
 * AI 模块统一异常 —— 封装 AI 调用过程中出现的所有错误.
 *
 * <h3>错误来源</h3>
 * <ul>
 *   <li>API 密钥未配置：{@code provider} 参数指示缺失配置的提供商</li>
 *   <li>网络请求失败：包装原始 {@link java.io.IOException}</li>
 *   <li>API 返回错误响应：携带 HTTP 状态码和响应体信息</li>
 *   <li>JSON 解析失败：包装 {@link com.alibaba.fastjson2.JSONException}</li>
 *   <li>速率限制触发：包含重试建议时间</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 抛出 AI 异常
 * throw new AiException("OpenAI API 调用失败", "openai");
 *
 * // 带原始异常
 * throw new AiException("请求超时", "deepseek", ioException);
 *
 * // 带 HTTP 状态码的错误
 * throw new AiException(429, "请求频率超限，请稍后重试", "openai");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AiException extends RuntimeException {

    /** 提供商名称，用于日志和调试定位 */
    private final String provider;

    /** HTTP 状态码（如 401、429、500），非 HTTP 错误时为 0 */
    private final int statusCode;

    /** 上游服务返回的错误码，如 invalid_api_key、rate_limit_exceeded */
    private final String errorCode;

    /** 上游服务返回的错误类型，如 invalid_request_error、server_error */
    private final String errorType;

    // ======================== 构造方法 ========================

    /**
     * 创建 AI 异常.
     *
     * @param message 错误描述
     * @param provider 提供商名称
     */
    public AiException(String message, String provider) {
        super(message);
        this.provider = provider;
        this.statusCode = 0;
        this.errorCode = null;
        this.errorType = null;
    }

    /**
     * 创建带原始异常的 AI 异常.
     *
     * @param message  错误描述
     * @param provider 提供商名称
     * @param cause    原始异常
     */
    public AiException(String message, String provider, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = 0;
        this.errorCode = null;
        this.errorType = null;
    }

    /**
     * 创建带 HTTP 状态码的 AI 异常.
     *
     * @param statusCode HTTP 状态码（如 429）
     * @param message    错误描述
     * @param provider   提供商名称
     */
    public AiException(int statusCode, String message, String provider) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
        this.errorCode = null;
        this.errorType = null;
    }

    /**
     * 创建带 HTTP 状态码和上游错误详情的 AI 异常.
     *
     * @param statusCode HTTP 状态码
     * @param message    错误描述
     * @param provider   提供商名称
     * @param errorCode  上游错误码
     * @param errorType  上游错误类型
     */
    public AiException(int statusCode, String message, String provider, String errorCode, String errorType) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    /**
     * 创建带 HTTP 状态码和原始异常的 AI 异常.
     *
     * @param statusCode HTTP 状态码
     * @param message    错误描述
     * @param provider   提供商名称
     * @param cause      原始异常
     */
    public AiException(int statusCode, String message, String provider, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
        this.errorCode = null;
        this.errorType = null;
    }

    /**
     * 创建带 HTTP 状态码、上游错误详情和原始异常的 AI 异常.
     *
     * @param statusCode HTTP 状态码
     * @param message    错误描述
     * @param provider   提供商名称
     * @param errorCode  上游错误码
     * @param errorType  上游错误类型
     * @param cause      原始异常
     */
    public AiException(int statusCode,
                       String message,
                       String provider,
                       String errorCode,
                       String errorType,
                       Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    // ======================== getter ========================

    /**
     * 获取提供商名称.
     *
     * @return 发生错误时请求的提供商名称
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 获取 HTTP 状态码.
     *
     * @return HTTP 状态码，非 HTTP 错误时为 0
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取上游服务错误码.
     *
     * @return 错误码，非上游结构化错误时为 {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取上游服务错误类型.
     *
     * @return 错误类型，非上游结构化错误时为 {@code null}
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * 判断是否为速率限制错误.
     *
     * @return {@code true} 当状态码为 429 时
     */
    public boolean isRateLimitExceeded() {
        return statusCode == 429;
    }

    /**
     * 判断是否为认证错误.
     *
     * @return {@code true} 当状态码为 401 或 403 时
     */
    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }
}
