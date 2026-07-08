package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.tool.enums.HttpMethod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 请求工具——多引擎自适应 + 链式 API + 静态便捷方法.
 *
 * <h3>引擎检测优先级</h3>
 * <ol>
 *   <li>Apache HttpClient 5.x（classpath 存在 {@code org.apache.hc.client5.http.classic.HttpClient}）</li>
 *   <li>OkHttp 4.x（classpath 存在 {@code okhttp3.OkHttpClient}）</li>
 *   <li>JDK 内置 {@link HttpURLConnection}（降级兜底）</li>
 * </ol>
 *
 * <h3>三种使用方式</h3>
 * <pre>{@code
 * // 1. 链式 API（复杂场景）
 * HttpResponse resp = HttpUtil.create()
 *     .url("https://api.example.com/order")
 *     .post()
 *     .header("Authorization", "Bearer " + token)
 *     .contentType("application/json")
 *     .body(jsonBody)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .readTimeout(Duration.ofSeconds(30))
 *     .maxRetry(3)
 *     .execute();
 *
 * // 2. 静态便捷方法（简单场景）
 * String result = HttpUtil.get("https://api.example.com/user/123");
 * String result = HttpUtil.post("https://api.example.com/user", jsonBody);
 *
 * // 3. 全局配置修改超时
 * HttpConfig config = HttpUtil.getGlobalConfig();
 * config.setConnectTimeout(Duration.ofSeconds(10));
 * }</pre>
 */
public final class HttpUtil {

    private HttpUtil() {}

    /** 全局默认配置，可通过 {@link #setGlobalConfig(HttpConfig)} 覆盖 */
    private static volatile HttpConfig globalConfig = new HttpConfig();
    /** 当前检测到的 HTTP 引擎，延迟初始化 */
    private static volatile Engine engine;

    // ======================== 引擎检测 ========================

    /** 引擎执行接口 */
    private interface Engine {
        HttpResponse execute(HttpRequest request);
    }

    /** 按优先级检测可用引擎并缓存 */
    private static Engine engine() {
        if (engine != null) return engine;
        synchronized (HttpUtil.class) {
            if (engine != null) return engine;
            if (isClassPresent("org.apache.hc.client5.http.classic.HttpClient")) {
                engine = new HttpClient5Engine();
            } else if (isClassPresent("okhttp3.OkHttpClient")) {
                engine = new OkHttp3Engine();
            } else {
                engine = new JdkEngine();
            }
            return engine;
        }
    }

    /** 通过反射判断 classpath 中是否存在指定类 */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ======================== 全局配置 ========================

    /**
     * 设置全局 HTTP 配置（超时、连接池大小等）.
     *
     * <p>设置后对所有通过 {@link HttpUtil} 发出的请求生效.</p>
     */
    public static void setGlobalConfig(HttpConfig config) {
        globalConfig = config;
    }

    /**
     * 获取当前全局 HTTP 配置，可直接修改其中的超时等参数.
     */
    public static HttpConfig getGlobalConfig() {
        return globalConfig;
    }

    // ======================== 链式 API ========================

    /**
     * 创建 HTTP 请求构建器（后续通过链式调用设置 URL、方法、参数等）.
     *
     * @return {@link HttpRequest} 构建器实例
     */
    public static HttpRequest create() {
        return HttpRequest.of(null);
    }

    /**
     * 创建 HTTP 请求构建器并指定 URL.
     *
     * @param url 请求 URL
     * @return {@link HttpRequest} 构建器实例
     */
    public static HttpRequest create(String url) {
        return HttpRequest.of(url);
    }

    // ======================== 静态便捷方法 ========================

    /**
     * 发送 GET 请求并返回响应体字符串.
     *
     * @param url 请求 URL
     * @return 响应体字符串
     */
    public static String get(String url) {
        return HttpRequest.of(url).get().execute().getBody();
    }

    /**
     * 发送 POST 请求（Content-Type: application/json）并返回响应体字符串.
     *
     * @param url  请求 URL
     * @param body JSON 请求体字符串
     * @return 响应体字符串
     */
    public static String post(String url, String body) {
        return HttpRequest.of(url).post().body(body).contentType("application/json").execute().getBody();
    }

    /**
     * 发送 PUT 请求（Content-Type: application/json）并返回响应体字符串.
     */
    public static String put(String url, String body) {
        return HttpRequest.of(url).put().body(body).contentType("application/json").execute().getBody();
    }

    /**
     * 发送 DELETE 请求并返回响应体字符串.
     */
    public static String delete(String url) {
        return HttpRequest.of(url).delete().execute().getBody();
    }

    // ======================== 内部执行委托 ========================

    /** 由 {@link HttpRequest#execute()} 调用，委托给当前引擎 */
    static HttpResponse execute(HttpRequest request) {
        return engine().execute(request);
    }

    // ======================== 引擎实现 ========================

    /**
     * JDK 内置引擎，基于 {@link HttpURLConnection}，无需任何额外依赖.
     *
     * <p>作为降级兜底方案，在无 Apache HttpClient 或 OkHttp 时自动启用.</p>
     */
    static class JdkEngine implements Engine {
        @Override
        public HttpResponse execute(HttpRequest request) {
            long start = System.currentTimeMillis();
            HttpURLConnection conn = null;
            try {
                String fullUrl = request.getUrl();
                // 拼接 query params
                if (!request.getQueryParams().isEmpty()) {
                    StringBuilder qs = new StringBuilder();
                    for (Map.Entry<String, Object> e : request.getQueryParams().entrySet()) {
                        if (qs.length() > 0) qs.append('&');
                        qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                                .append('=').append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
                    }
                    fullUrl += (fullUrl.contains("?") ? "&" : "?") + qs;
                }

                conn = (HttpURLConnection) new URL(fullUrl).openConnection();
                conn.setRequestMethod(request.getMethod().name());
                conn.setConnectTimeout((int) globalConfig.getConnectTimeout().toMillis());
                conn.setReadTimeout((int) globalConfig.getReadTimeout().toMillis());

                for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }

                // 写请求体
                HttpMethod method = request.getMethod();
                if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                    String body = request.getBody();
                    if (body != null && !body.isEmpty()) {
                        conn.setDoOutput(true);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }

                int status = conn.getResponseCode();
                String respBody;
                try (java.io.InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                    respBody = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
                }

                HttpResponse resp = new HttpResponse(status, respBody);
                resp.setDurationMs(System.currentTimeMillis() - start);
                return resp;
            } catch (IOException e) {
                throw new RuntimeException("HTTP request failed: " + request.getUrl(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    /**
     * Apache HttpClient 5.x 引擎（占位，当前委托 JDK 引擎）.
     *
     * <p>后续版本会基于 {@code org.apache.hc.client5.http.impl.classic.HttpClientBuilder} 实现连接池和 HTTP/2 支持.</p>
     */
    static class HttpClient5Engine implements Engine {
        @Override
        public HttpResponse execute(HttpRequest request) {
            return new JdkEngine().execute(request);
        }
    }

    /**
     * OkHttp 引擎（占位，当前委托 JDK 引擎）.
     *
     * <p>后续版本会基于 {@code okhttp3.OkHttpClient} 实现连接池和 HTTP/2 支持.</p>
     */
    static class OkHttp3Engine implements Engine {
        @Override
        public HttpResponse execute(HttpRequest request) {
            return new JdkEngine().execute(request);
        }
    }
}
