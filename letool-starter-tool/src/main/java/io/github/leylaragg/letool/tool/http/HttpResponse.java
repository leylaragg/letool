package io.github.leylaragg.letool.tool.http;

import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 不可变 HTTP 响应，包含状态码、响应体、响应头、总耗时和实际尝试次数。
 */
public final class HttpResponse {

    /** Content-Type 中字符集参数的匹配规则。 */
    private static final Pattern CHARSET_PATTERN = Pattern.compile(
            "(?i)(?:^|;)\\s*charset\\s*=\\s*\\\"?([^;\\\"\\s]+)");

    /** HTTP 状态码。 */
    private final int statusCode;

    /** 防御性复制后的响应体字节。 */
    private final byte[] bodyBytes;

    /** 根据响应字符集解码后的文本响应体。 */
    private final String body;

    /** 不可变多值响应头。 */
    private final Map<String, List<String>> headers;

    /** 从首次发送到最终响应完成的总耗时。 */
    private final Duration duration;

    /** 包含首次请求在内的实际尝试次数。 */
    private final int attempts;

    /**
     * 创建不可变 HTTP 响应。
     *
     * @param statusCode HTTP 状态码
     * @param bodyBytes 完整响应体字节
     * @param httpHeaders JDK HTTP 响应头
     * @param duration 请求总耗时
     * @param attempts 实际尝试次数
     */
    HttpResponse(int statusCode,
                 byte[] bodyBytes,
                 HttpHeaders httpHeaders,
                 Duration duration,
                 int attempts) {
        this.statusCode = statusCode;
        this.bodyBytes = bodyBytes.clone();
        this.headers = httpHeaders.map();
        this.duration = duration;
        this.attempts = attempts;
        this.body = new String(this.bodyBytes, resolveCharset(httpHeaders));
    }

    /**
     * 判断响应是否为 2xx 成功状态。
     *
     * @return 状态码位于 200 至 299 时返回 {@code true}
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 判断响应是否为 2xx 状态。
     *
     * @return 2xx 状态返回 {@code true}
     */
    public boolean is2xx() {
        return isSuccess();
    }

    /**
     * 判断响应是否为 3xx 重定向状态。
     *
     * @return 3xx 状态返回 {@code true}
     */
    public boolean is3xx() {
        return statusCode >= 300 && statusCode < 400;
    }

    /**
     * 判断响应是否为 4xx 客户端错误状态。
     *
     * @return 4xx 状态返回 {@code true}
     */
    public boolean is4xx() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 判断响应是否为 5xx 服务端错误状态。
     *
     * @return 5xx 状态返回 {@code true}
     */
    public boolean is5xx() {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取根据 Content-Type 字符集解码的文本响应体。
     *
     * @return 文本响应体，空响应返回空字符串
     */
    public String getBody() {
        return body;
    }

    /**
     * 获取响应体字节的防御性副本。
     *
     * @return 响应体字节副本
     */
    public byte[] getBodyBytes() {
        return bodyBytes.clone();
    }

    /**
     * 获取不可修改的多值响应头。
     *
     * @return 响应头映射
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * 按不区分大小写的名称获取第一个响应头值。
     *
     * @param name 响应头名称
     * @return 第一个响应头值，不存在时返回 {@code null}
     */
    public String header(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    /**
     * 获取请求总耗时。
     *
     * @return 从首次发送到最终完成的耗时
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * 获取请求总耗时毫秒数。
     *
     * @return 总耗时毫秒数
     */
    public long getDurationMs() {
        return duration.toMillis();
    }

    /**
     * 获取包含首次发送在内的实际尝试次数。
     *
     * @return 至少为一的尝试次数
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * 根据 Content-Type 响应头解析字符集，缺失或非法时使用 UTF-8。
     *
     * @param headers HTTP 响应头
     * @return 可用于解码响应体的字符集
     */
    private static Charset resolveCharset(HttpHeaders headers) {
        String contentType = headers.firstValue("Content-Type").orElse("");
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (IllegalArgumentException ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
