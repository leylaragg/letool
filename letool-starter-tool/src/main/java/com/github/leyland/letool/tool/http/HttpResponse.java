package com.github.leyland.letool.tool.http;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 响应模型——封装状态码、响应体、响应头、耗时等信息.
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HttpResponse resp = HttpUtil.get("https://api.example.com/user/123");
 *
 * // 状态判断
 * if (resp.isSuccess()) {
 *     User user = JsonUtil.parseObject(resp.getBody(), User.class);
 * }
 *
 * // 响应头
 * String contentType = resp.header("Content-Type");
 *
 * // 耗时
 * log.info("Request took {}ms", resp.getDurationMs());
 * }</pre>
 */
public class HttpResponse {

    /** HTTP 状态码 */
    private int statusCode;
    /** 响应体字符串 */
    private String body;
    /** 响应体字节数组 */
    private byte[] bodyBytes;
    /** 响应头映射 */
    private Map<String, String> headers;
    /** 请求耗时（毫秒） */
    private long durationMs;

    /** 创建空响应 */
    public HttpResponse() {
        this.headers = new HashMap<>();
    }

    /**
     * 创建带状态码和响应体的响应.
     *
     * @param statusCode HTTP 状态码
     * @param body       响应体字符串
     */
    public HttpResponse(int statusCode, String body) {
        this();
        this.statusCode = statusCode;
        this.body = body;
    }

    // ======================== 状态判断 ========================

    /**
     * 是否请求成功（2xx）.
     *
     * @return {@code true} 如果 200 ≤ statusCode < 300
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** 是否为 2xx 响应 */
    public boolean is2xx() { return isSuccess(); }
    /** 是否为 3xx 重定向 */
    public boolean is3xx() { return statusCode >= 300 && statusCode < 400; }
    /** 是否为 4xx 客户端错误 */
    public boolean is4xx() { return statusCode >= 400 && statusCode < 500; }
    /** 是否为 5xx 服务端错误 */
    public boolean is5xx() { return statusCode >= 500; }

    // ======================== getter / setter ========================

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    /**
     * 获取响应体字节数组（适用于下载文件等二进制场景）.
     */
    public byte[] getBodyBytes() { return bodyBytes; }
    public void setBodyBytes(byte[] bodyBytes) { this.bodyBytes = bodyBytes; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    /**
     * 获取请求耗时（毫秒）.
     *
     * @return 从发送请求到收到响应的耗时
     */
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    /**
     * 获取指定名称的响应头值.
     *
     * @param name 响应头名称（如 "Content-Type"）
     * @return 响应头值，不存在返回 {@code null}
     */
    public String header(String name) {
        return headers.get(name);
    }
}
