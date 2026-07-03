package com.github.leyland.letool.ai.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AI HTTP 请求描述。
 *
 * <p>该类型只表达 letool AI 模块需要的最小 HTTP 信息，避免把底层实现绑定到
 * 某个第三方 HTTP 客户端。默认实现使用 JDK {@code HttpClient}，用户也可以通过
 * Spring Bean 替换为自己的传输层。</p>
 *
 * @param url                  请求 URL
 * @param headers              请求头
 * @param body                 请求体
 * @param connectTimeoutMillis 连接超时时间，单位毫秒
 * @param readTimeoutMillis    读取/请求超时时间，单位毫秒
 */
public record AiHttpRequest(String url,
                            Map<String, String> headers,
                            String body,
                            int connectTimeoutMillis,
                            int readTimeoutMillis) {

    /**
     * 创建不可变请求对象。
     */
    public AiHttpRequest {
        Objects.requireNonNull(url, "url must not be null");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
    }

    /**
     * 创建 JSON POST 请求。
     *
     * @param url                  请求 URL
     * @param headers              额外请求头
     * @param jsonBody             JSON 请求体
     * @param connectTimeoutMillis 连接超时时间
     * @param readTimeoutMillis    读取/请求超时时间
     * @return AI HTTP 请求
     */
    public static AiHttpRequest postJson(String url,
                                         Map<String, String> headers,
                                         String jsonBody,
                                         int connectTimeoutMillis,
                                         int readTimeoutMillis) {
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        mergedHeaders.put("Content-Type", "application/json");
        if (headers != null) {
            mergedHeaders.putAll(headers);
        }
        return new AiHttpRequest(url, mergedHeaders, jsonBody, connectTimeoutMillis, readTimeoutMillis);
    }
}
