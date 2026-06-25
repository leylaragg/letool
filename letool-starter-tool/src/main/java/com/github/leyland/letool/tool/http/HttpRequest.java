package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.tool.enums.HttpMethod;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * HTTP 请求构建器——链式 API 构建请求并发送.
 *
 * <h3>设计理念</h3>
 * <p>通过方法链（Fluent API）逐步构建请求的各个要素，最后调用 {@link #execute()} 发送.
 * 链式调用顺序无限制，可任意组合，最终发送时一次性收集所有配置.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // POST JSON
 * HttpResponse resp = HttpRequest.of("https://api.example.com/order")
 *     .post()
 *     .contentType("application/json")
 *     .header("Authorization", "Bearer " + token)
 *     .body(jsonBody)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .maxRetry(3)
 *     .retryOn(503, 504)
 *     .execute();
 *
 * // GET 查询
 * String html = HttpRequest.of("https://example.com/search")
 *     .get()
 *     .queryParam("q", "Java")
 *     .queryParam("page", 1)
 *     .execute()
 *     .getBody();
 *
 * // 文件上传
 * HttpRequest.of("https://api.example.com/upload")
 *     .post()
 *     .multipart()
 *     .formField("title", "Report")
 *     .formFile("file", new File("report.pdf"))
 *     .execute();
 * }</pre>
 */
public class HttpRequest {

    private String url;
    private HttpMethod method = HttpMethod.GET;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> queryParams = new LinkedHashMap<>();
    private String body;
    private byte[] bodyBytes;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Duration writeTimeout;
    private Charset charset = StandardCharsets.UTF_8;
    private boolean trustAllCerts = false;
    private int maxRetries = 0;
    private Set<Integer> retryOnStatus = new HashSet<>();
    private List<HttpInterceptor> interceptors = new ArrayList<>();
    private Map<String, String> formFields = new LinkedHashMap<>();
    private Map<String, File> formFiles = new LinkedHashMap<>();

    // ======================== 工厂方法 ========================

    /**
     * 创建请求构建器（不指定 URL，后续通过 {@link #url(String)} 设置）.
     *
     * @param url 请求 URL（可为 {@code null}）
     * @return 新的 HttpRequest 构建器实例
     */
    public static HttpRequest of(String url) {
        HttpRequest req = new HttpRequest();
        req.url = url;
        return req;
    }

    // ======================== HTTP 方法 ========================

    /** 设置 HTTP 方法 */
    public HttpRequest method(HttpMethod method) { this.method = method; return this; }
    /** 设置为 GET 请求 */
    public HttpRequest get()  { this.method = HttpMethod.GET; return this; }
    /** 设置为 POST 请求 */
    public HttpRequest post() { this.method = HttpMethod.POST; return this; }
    /** 设置为 PUT 请求 */
    public HttpRequest put()  { this.method = HttpMethod.PUT; return this; }
    /** 设置为 DELETE 请求 */
    public HttpRequest delete()  { this.method = HttpMethod.DELETE; return this; }
    /** 设置为 PATCH 请求 */
    public HttpRequest patch() { this.method = HttpMethod.PATCH; return this; }

    // ======================== URL / 参数 / 请求头 ========================

    /** 设置请求 URL */
    public HttpRequest url(String url) { this.url = url; return this; }

    /**
     * 添加 Query 参数（自动拼接到 URL 后面）.
     *
     * @param key   参数名
     * @param value 参数值
     */
    public HttpRequest queryParam(String key, Object value) {
        this.queryParams.put(key, value);
        return this;
    }

    /** 添加请求头 */
    public HttpRequest header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    /** 批量添加请求头 */
    public HttpRequest headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    /** 设置 Content-Type 请求头（快捷方式） */
    public HttpRequest contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    /** 设置 Authorization 请求头（原始值） */
    public HttpRequest authorization(String value) {
        return header("Authorization", value);
    }

    /** 设置 Bearer Token 认证头（自动添加 "Bearer " 前缀） */
    public HttpRequest bearerToken(String token) {
        return authorization("Bearer " + token);
    }

    /** 设置请求体（字符串） */
    public HttpRequest body(String body) { this.body = body; return this; }
    /** 设置请求体（字节数组） */
    public HttpRequest body(byte[] bodyBytes) { this.bodyBytes = bodyBytes; return this; }

    // ======================== 超时 ========================

    /**
     * 设置连接超时（覆盖全局配置）.
     *
     * @param duration 超时时长
     */
    public HttpRequest connectTimeout(Duration duration) {
        this.connectTimeout = duration;
        return this;
    }

    /**
     * 设置读取超时（覆盖全局配置）.
     *
     * @param millis 超时毫秒数
     */
    public HttpRequest readTimeout(long millis) {
        this.readTimeout = Duration.ofMillis(millis);
        return this;
    }

    /**
     * 设置写入超时（覆盖全局配置）.
     *
     * @param writeTimeout 超时时长
     */
    public HttpRequest writeTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
        return this;
    }

    // ======================== SSL ========================

    /**
     * 设置是否信任所有 SSL 证书（仅开发环境使用）.
     *
     * @param trust {@code true} 跳过证书校验
     */
    public HttpRequest trustAllCerts(boolean trust) { this.trustAllCerts = trust; return this; }

    // ======================== 重试 ========================

    /**
     * 设置最大重试次数.
     *
     * @param maxRetries 最大重试次数（0 表示不重试）
     */
    public HttpRequest maxRetry(int maxRetries) { this.maxRetries = maxRetries; return this; }

    /**
     * 设置遇到哪些 HTTP 状态码时触发重试.
     *
     * @param statusCodes 状态码列表（如 503, 504）
     */
    public HttpRequest retryOn(int... statusCodes) {
        for (int code : statusCodes) this.retryOnStatus.add(code);
        return this;
    }

    // ======================== 拦截器 ========================

    /**
     * 添加 HTTP 拦截器（按添加顺序执行）.
     *
     * @param interceptor 拦截器实例
     */
    public HttpRequest interceptor(HttpInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    /** 批量添加拦截器 */
    public HttpRequest interceptors(List<HttpInterceptor> interceptors) {
        this.interceptors.addAll(interceptors);
        return this;
    }

    // ======================== Multipart ========================

    /** 设置为 multipart/form-data 请求（自动设置 Content-Type） */
    public HttpRequest multipart() {
        return contentType("multipart/form-data");
    }

    /**
     * 添加表单字段.
     *
     * @param name  字段名
     * @param value 字段值
     */
    public HttpRequest formField(String name, String value) {
        this.formFields.put(name, value);
        return this;
    }

    /**
     * 添加上传文件.
     *
     * @param name 表单字段名
     * @param file 文件对象
     */
    public HttpRequest formFile(String name, File file) {
        this.formFiles.put(name, file);
        return this;
    }

    // ======================== 发送 ========================

    /**
     * 执行 HTTP 请求并返回响应.
     *
     * <p>实际执行委托给 {@link HttpUtil#execute(HttpRequest)}，由已检测到的 HTTP 引擎处理.</p>
     *
     * @return HTTP 响应（包含状态码、响应体、耗时等）
     */
    public HttpResponse execute() {
        return HttpUtil.execute(this);
    }

    // ======================== getter（由 HttpUtil 和引擎内部使用） ========================

    public String getUrl() { return url; }
    public HttpMethod getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, Object> getQueryParams() { return queryParams; }
    public String getBody() { return body; }
    public byte[] getBodyBytes() { return bodyBytes; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public Duration getWriteTimeout() { return writeTimeout; }
    public Charset getCharset() { return charset; }
    public boolean isTrustAllCerts() { return trustAllCerts; }
    public int getMaxRetries() { return maxRetries; }
    public Set<Integer> getRetryOnStatus() { return retryOnStatus; }
    public List<HttpInterceptor> getInterceptors() { return interceptors; }
    public Map<String, String> getFormFields() { return formFields; }
    public Map<String, File> getFormFiles() { return formFiles; }
}
