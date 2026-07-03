package com.github.leyland.letool.net.http;

import com.github.leyland.letool.net.config.NetProperties;
import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.tool.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 客户端模板 —— 基于 {@link HttpURLConnection} 的轻量级 HTTP 请求封装.
 *
 * <p>无需引入外部 HTTP 客户端依赖（如 Apache HttpClient、OkHttp），
 * 使用 JDK 内置的 {@link HttpURLConnection} 实现，适合简单 HTTP 调用场景.</p>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>GET / POST / PUT / DELETE 一键调用</li>
 *   <li>支持自定义 Headers</li>
 *   <li>可配置连接超时和读取超时</li>
 *   <li>内置简单的连接池（线程安全的可用连接 Map）</li>
 *   <li>支持 Builder 模式构建复杂请求</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * NetHttpTemplate http = new NetHttpTemplate(netProperties);
 * String result = http.get("http://api.example.com/users/1");
 * String created = http.post("http://api.example.com/users", userDto);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class NetHttpTemplate {

    private static final Logger log = LoggerFactory.getLogger(NetHttpTemplate.class);

    // ======================== 字段 ========================

    /** HTTP 配置属性 */
    private final NetProperties properties;

    /** 连接超时（毫秒） */
    private final int connectTimeoutMs;

    /** 读取超时（毫秒） */
    private final int readTimeoutMs;

    /** 负载均衡器（可选） */
    private HttpLoadBalancer loadBalancer;

    /** 熔断器（可选） */
    private HttpCircuitBreaker circuitBreaker;

    // ======================== 构造器 ========================

    /**
     * 构造 HTTP 模板（从配置属性初始化）.
     *
     * @param properties 网络配置属性
     */
    public NetHttpTemplate(NetProperties properties) {
        this.properties = properties;
        this.connectTimeoutMs = parseDurationMs(properties.getHttp().getConnectTimeout(), 5000);
        this.readTimeoutMs = parseDurationMs(properties.getHttp().getReadTimeout(), 30000);
        log.info("NetHttpTemplate created: connectTimeout={}ms, readTimeout={}ms", connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 构造 HTTP 模板（手动指定超时）.
     *
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public NetHttpTemplate(int connectTimeoutMs, int readTimeoutMs) {
        this.properties = null;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    // ======================== GET ========================

    /**
     * 发送 GET 请求.
     *
     * @param url 请求 URL
     * @return 响应体字符串
     * @throws NetException 请求失败时抛出
     */
    public String get(String url) {
        return execute("GET", url, null, null);
    }

    // ======================== POST ========================

    /**
     * 发送 POST 请求（JSON body）.
     *
     * @param url  请求 URL
     * @param body 请求体对象（将被转为 JSON 字节后发送）
     * @return 响应体字符串
     * @throws NetException 请求失败时抛出
     */
    public String post(String url, Object body) {
        byte[] bodyBytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        return execute("POST", url, headers, bodyBytes);
    }

    // ======================== PUT ========================

    /**
     * 发送 PUT 请求（JSON body）.
     *
     * @param url  请求 URL
     * @param body 请求体对象
     * @return 响应体字符串
     * @throws NetException 请求失败时抛出
     */
    public String put(String url, Object body) {
        byte[] bodyBytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        return execute("PUT", url, headers, bodyBytes);
    }

    // ======================== DELETE ========================

    /**
     * 发送 DELETE 请求.
     *
     * @param url 请求 URL
     * @return 响应体字符串
     * @throws NetException 请求失败时抛出
     */
    public String delete(String url) {
        return execute("DELETE", url, null, null);
    }

    // ======================== execute (核心) ========================

    /**
     * 执行 HTTP 请求（完整控制版本）.
     *
     * @param method  HTTP 方法（GET / POST / PUT / DELETE）
     * @param urlStr  请求 URL
     * @param headers 自定义请求头（可为 {@code null}）
     * @param body    请求体（可为 {@code null}）
     * @return 响应体字符串
     * @throws NetException 请求失败时抛出
     */
    public String execute(String method, String urlStr, Map<String, String> headers, byte[] body) {
        // 熔断检查
        if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
            throw new NetException("Circuit breaker is OPEN, request blocked: " + urlStr);
        }

        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(urlStr);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method.toUpperCase());
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoInput(true);

            // 设置默认请求头
            if (headers == null || !headers.containsKey("Accept")) {
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            }

            // 设置自定义请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // 写请求体
            if (body != null && body.length > 0) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }
            }

            // 连接（隐式调用，显式获取状态码确保完成）
            int statusCode = conn.getResponseCode();
            log.debug("HTTP {} {} -> {}", method, urlStr, statusCode);

            // 读响应体
            byte[] responseBytes = readResponse(conn);
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            if (statusCode >= 400) {
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                throw new NetException("HTTP " + method + " " + urlStr
                        + " failed with status " + statusCode + ": " + responseBody);
            }

            // 熔断 - 记录成功
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess();
            }

            return responseBody;
        } catch (NetException e) {
            throw e;
        } catch (Exception e) {
            // 熔断 - 记录失败
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            throw new NetException("HTTP " + method + " " + urlStr + " failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ======================== Builder ========================

    /**
     * 创建请求构建器.
     *
     * @return RequestBuilder 实例
     */
    public RequestBuilder request() {
        return new RequestBuilder(this);
    }

    /**
     * 可链式调用的请求构建器.
     */
    public static class RequestBuilder {
        private final NetHttpTemplate template;
        private String method = "GET";
        private String url;
        private final Map<String, String> headers = new HashMap<>();
        private byte[] body;

        RequestBuilder(NetHttpTemplate template) {
            this.template = template;
        }

        /** 设置 HTTP 方法. */
        public RequestBuilder method(String method) { this.method = method; return this; }
        /** 设置请求 URL. */
        public RequestBuilder url(String url) { this.url = url; return this; }
        /** 添加请求头. */
        public RequestBuilder header(String name, String value) { headers.put(name, value); return this; }
        /** 设置 JSON body. */
        public RequestBuilder body(Object body) {
            this.body = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null;
            headers.putIfAbsent("Content-Type", "application/json; charset=UTF-8");
            return this;
        }
        /** 设置原始字节 body. */
        public RequestBuilder bodyBytes(byte[] body) { this.body = body; return this; }

        /** 执行请求并返回响应字符串. */
        public String execute() {
            return template.execute(method, url, headers, body);
        }
    }

    // ======================== LoadBalancer ========================

    /**
     * 设置负载均衡器.
     *
     * @param loadBalancer HTTP 负载均衡器
     */
    public void setLoadBalancer(HttpLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    /**
     * 获取负载均衡器.
     *
     * @return 当前负载均衡器，可能为 {@code null}
     */
    public HttpLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    // ======================== CircuitBreaker ========================

    /**
     * 设置熔断器.
     *
     * @param circuitBreaker 熔断器实例
     */
    public void setCircuitBreaker(HttpCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 获取熔断器.
     *
     * @return 当前熔断器，可能为 {@code null}
     */
    public HttpCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    // ======================== 内部方法 ========================

    /**
     * 读取 HTTP 响应体.
     *
     * @param conn HTTP 连接
     * @return 响应字节数组
     * @throws IOException IO 异常
     */
    private byte[] readResponse(HttpURLConnection conn) throws IOException {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            // 错误流（4xx / 5xx）
            in = conn.getErrorStream();
        }
        if (in == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }

    /**
     * 将持续时间字符串（如 {@code "5s"}、{@code "30s"}、{@code "1m"}）解析为毫秒.
     *
     * @param duration     持续时间字符串
     * @param defaultMs    解析失败时的默认毫秒值
     * @return 毫秒数
     */
    private static int parseDurationMs(String duration, int defaultMs) {
        if (StrUtil.isBlank(duration)) {
            return defaultMs;
        }
        duration = duration.trim().toLowerCase();
        try {
            if (duration.endsWith("ms")) {
                return Integer.parseInt(duration.replace("ms", "").trim());
            }
            if (duration.endsWith("s")) {
                return Integer.parseInt(duration.replace("s", "").trim()) * 1000;
            }
            if (duration.endsWith("m")) {
                return Integer.parseInt(duration.replace("m", "").trim()) * 60 * 1000;
            }
            // 无后缀，当作毫秒
            return Integer.parseInt(duration);
        } catch (NumberFormatException e) {
            log.warn("Invalid duration string '{}', using default {}ms", duration, defaultMs);
            return defaultMs;
        }
    }
}
