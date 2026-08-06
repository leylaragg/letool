package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * Tool 模块 HTTP 便利能力公开的稳定错误码。
 */
public enum HttpErrorCode implements ErrorCode {

    /** 请求地址、请求头或请求体配置不合法。 */
    INVALID_REQUEST("TOOL_HTTP_001", "HTTP 请求参数无效"),

    /** DNS、连接、TLS 或响应读取等传输过程失败。 */
    REQUEST_FAILED("TOOL_HTTP_002", "HTTP 请求执行失败"),

    /** 请求超过配置的总超时时间。 */
    REQUEST_TIMEOUT("TOOL_HTTP_003", "HTTP 请求超时"),

    /** 请求线程在发送或重试等待期间被中断。 */
    REQUEST_INTERRUPTED("TOOL_HTTP_004", "HTTP 请求被中断"),

    /** 响应体超过允许读取到内存的最大字节数。 */
    RESPONSE_TOO_LARGE("TOOL_HTTP_005", "HTTP 响应体超过允许大小"),

    /** 用户提供的 HTTP 拦截器执行失败。 */
    INTERCEPTOR_FAILED("TOOL_HTTP_006", "HTTP 拦截器执行失败");

    /** 稳定错误码。 */
    private final String code;

    /** 不包含请求敏感信息的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建 HTTP 错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 安全默认消息
     */
    HttpErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码字符串
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取未配置消息资源时使用的安全默认消息。
     *
     * @return 默认消息
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
