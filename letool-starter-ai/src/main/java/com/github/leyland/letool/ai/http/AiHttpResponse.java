package com.github.leyland.letool.ai.http;

/**
 * AI HTTP 响应描述。
 *
 * <p>仅保留 provider 需要判断的状态码与响应体，避免向上层泄漏底层 HTTP 客户端类型。</p>
 *
 * @param statusCode HTTP 状态码
 * @param body       响应体
 */
public record AiHttpResponse(int statusCode, String body) {

    /**
     * 创建响应对象，并将空响应体归一化为空字符串。
     */
    public AiHttpResponse {
        body = body == null ? "" : body;
    }
}
