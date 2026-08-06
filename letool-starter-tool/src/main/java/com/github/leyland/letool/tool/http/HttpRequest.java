package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.tool.enums.HttpMethod;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 非线程安全的 HTTP 链式请求构建器。
 *
 * <p>调用 {@link #execute()} 时会先生成不可变请求快照，发送期间继续修改构建器不会改变已经开始的请求。
 * 同一个构建器不应由多个线程并发修改或执行；需要并发调用时请为每次调用创建独立请求。</p>
 */
public final class HttpRequest {

    /** 默认重试等待时间。 */
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(100);

    /** 实际执行请求的实例化模板。 */
    private final HttpTemplate template;

    /** 请求地址。 */
    private String url;

    /** HTTP 请求方法。 */
    private HttpMethod method = HttpMethod.GET;

    /** 请求头，名称对应一个或多个值。 */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    /** 查询参数，名称对应一个或多个值。 */
    private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

    /** 文本请求体。 */
    private String body;

    /** 二进制请求体。 */
    private byte[] bodyBytes;

    /** 请求级总超时。 */
    private Duration timeout;

    /** 文本请求体与 Multipart 文本字段字符集。 */
    private Charset charset = StandardCharsets.UTF_8;

    /** 最大重试次数，不包含首次请求。 */
    private int maxRetries;

    /** 触发重试的 HTTP 状态码。 */
    private final Set<Integer> retryOnStatus = new LinkedHashSet<>();

    /** 两次尝试之间的固定等待时间。 */
    private Duration retryDelay = DEFAULT_RETRY_DELAY;

    /** 是否显式允许 POST、PATCH 等非幂等请求重试。 */
    private boolean retryNonIdempotent;

    /** 请求拦截器。 */
    private final List<HttpInterceptor> interceptors = new ArrayList<>();

    /** 是否使用 Multipart 请求体。 */
    private boolean multipart;

    /** Multipart 文本字段。 */
    private final List<FormField> formFields = new ArrayList<>();

    /** Multipart 文件字段。 */
    private final List<FormFile> formFiles = new ArrayList<>();

    /**
     * 创建请求构建器。
     *
     * @param template 执行请求的 HTTP 模板
     * @param url 初始请求地址，允许稍后通过 {@link #url(String)} 设置
     */
    private HttpRequest(HttpTemplate template, String url) {
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.url = url;
    }

    /**
     * 使用静态默认模板创建请求构建器。
     *
     * @param url 请求地址，允许为 {@code null} 并稍后设置
     * @return 新请求构建器
     */
    public static HttpRequest of(String url) {
        return new HttpRequest(HttpUtil.defaultTemplate(), url);
    }

    /**
     * 使用指定模板创建请求构建器。
     *
     * @param template 请求执行模板
     * @param url 初始请求地址
     * @return 新请求构建器
     */
    static HttpRequest of(HttpTemplate template, String url) {
        return new HttpRequest(template, url);
    }

    /**
     * 设置 HTTP 请求方法。
     *
     * @param method 请求方法
     * @return 当前构建器
     */
    public HttpRequest method(HttpMethod method) {
        this.method = Objects.requireNonNull(method, "method must not be null");
        return this;
    }

    /** @return 设置为 GET 方法的当前构建器 */
    public HttpRequest get() {
        return method(HttpMethod.GET);
    }

    /** @return 设置为 POST 方法的当前构建器 */
    public HttpRequest post() {
        return method(HttpMethod.POST);
    }

    /** @return 设置为 PUT 方法的当前构建器 */
    public HttpRequest put() {
        return method(HttpMethod.PUT);
    }

    /** @return 设置为 DELETE 方法的当前构建器 */
    public HttpRequest delete() {
        return method(HttpMethod.DELETE);
    }

    /** @return 设置为 PATCH 方法的当前构建器 */
    public HttpRequest patch() {
        return method(HttpMethod.PATCH);
    }

    /**
     * 设置请求地址。
     *
     * @param url HTTP 或 HTTPS 地址
     * @return 当前构建器
     */
    public HttpRequest url(String url) {
        this.url = url;
        return this;
    }

    /**
     * 追加查询参数；同名参数会按添加顺序保留。
     *
     * @param key 参数名称
     * @param value 参数值，{@code null} 按空字符串处理
     * @return 当前构建器
     */
    public HttpRequest queryParam(String key, Object value) {
        queryParams.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(value == null ? "" : String.valueOf(value));
        return this;
    }

    /**
     * 设置单值请求头；再次设置同名请求头时替换已有值。
     *
     * @param key 请求头名称
     * @param value 请求头值
     * @return 当前构建器
     */
    public HttpRequest header(String key, String value) {
        headers.put(key, new ArrayList<>(List.of(value)));
        return this;
    }

    /**
     * 追加同名请求头值。
     *
     * @param key 请求头名称
     * @param value 请求头值
     * @return 当前构建器
     */
    public HttpRequest addHeader(String key, String value) {
        headers.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * 批量设置单值请求头。
     *
     * @param values 请求头映射
     * @return 当前构建器
     */
    public HttpRequest headers(Map<String, String> values) {
        Objects.requireNonNull(values, "headers must not be null")
                .forEach(this::header);
        return this;
    }

    /**
     * 设置 Content-Type 请求头。
     *
     * @param contentType 媒体类型
     * @return 当前构建器
     */
    public HttpRequest contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    /**
     * 设置原始 Authorization 请求头。
     *
     * @param value 认证头完整值
     * @return 当前构建器
     */
    public HttpRequest authorization(String value) {
        return header("Authorization", value);
    }

    /**
     * 设置 Bearer Token 认证头。
     *
     * @param token 不包含 Bearer 前缀的令牌
     * @return 当前构建器
     */
    public HttpRequest bearerToken(String token) {
        return authorization("Bearer " + token);
    }

    /**
     * 设置文本请求体并清除二进制请求体。
     *
     * @param body 文本请求体，允许为 {@code null}
     * @return 当前构建器
     */
    public HttpRequest body(String body) {
        this.body = body;
        this.bodyBytes = null;
        return this;
    }

    /**
     * 设置二进制请求体并清除文本请求体。
     *
     * @param bodyBytes 二进制请求体，允许为 {@code null}
     * @return 当前构建器
     */
    public HttpRequest body(byte[] bodyBytes) {
        this.bodyBytes = bodyBytes == null ? null : bodyBytes.clone();
        this.body = null;
        return this;
    }

    /**
     * 设置单次请求总超时。
     *
     * @param timeout 严格大于零的总超时
     * @return 当前构建器
     */
    public HttpRequest timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 设置文本请求体和 Multipart 文本字段字符集。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public HttpRequest charset(Charset charset) {
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
        return this;
    }

    /**
     * 设置最大重试次数，不包含首次请求。
     *
     * @param maxRetries 0 至 10 之间的重试次数
     * @return 当前构建器
     */
    public HttpRequest maxRetry(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * 设置触发重试的 HTTP 状态码。
     *
     * @param statusCodes 100 至 599 之间的状态码
     * @return 当前构建器
     */
    public HttpRequest retryOn(int... statusCodes) {
        if (statusCodes == null) {
            throw new IllegalArgumentException("statusCodes must not be null");
        }
        for (int statusCode : statusCodes) {
            retryOnStatus.add(statusCode);
        }
        return this;
    }

    /**
     * 设置两次请求尝试之间的固定等待时间。
     *
     * @param retryDelay 非负等待时间
     * @return 当前构建器
     */
    public HttpRequest retryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
        return this;
    }

    /**
     * 显式设置是否允许非幂等请求重试。
     *
     * @param allowed {@code true} 表示调用方承担重复提交风险
     * @return 当前构建器
     */
    public HttpRequest retryNonIdempotent(boolean allowed) {
        this.retryNonIdempotent = allowed;
        return this;
    }

    /**
     * 添加请求拦截器。
     *
     * @param interceptor 请求生命周期拦截器
     * @return 当前构建器
     */
    public HttpRequest interceptor(HttpInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor, "interceptor must not be null"));
        return this;
    }

    /**
     * 批量添加请求拦截器。
     *
     * @param values 请求生命周期拦截器
     * @return 当前构建器
     */
    public HttpRequest interceptors(List<HttpInterceptor> values) {
        Objects.requireNonNull(values, "interceptors must not be null").forEach(this::interceptor);
        return this;
    }

    /**
     * 将请求体切换为 Multipart 表单。
     *
     * @return 当前构建器
     */
    public HttpRequest multipart() {
        this.multipart = true;
        return this;
    }

    /**
     * 添加 Multipart 文本字段。
     *
     * @param name 字段名称
     * @param value 字段值
     * @return 当前构建器
     */
    public HttpRequest formField(String name, String value) {
        multipart = true;
        formFields.add(new FormField(name, value));
        return this;
    }

    /**
     * 添加 Multipart 文件字段。
     *
     * @param name 字段名称
     * @param file 待上传文件
     * @return 当前构建器
     */
    public HttpRequest formFile(String name, File file) {
        Objects.requireNonNull(file, "file must not be null");
        return formFile(name, file.toPath());
    }

    /**
     * 添加 Multipart 文件字段。
     *
     * @param name 字段名称
     * @param path 待上传文件路径
     * @return 当前构建器
     */
    public HttpRequest formFile(String name, Path path) {
        multipart = true;
        formFiles.add(new FormFile(name, path));
        return this;
    }

    /**
     * 执行请求并返回不可变响应。
     *
     * @return HTTP 响应
     * @throws HttpException 请求配置、传输、超时或响应读取失败时抛出
     */
    public HttpResponse execute() {
        return template.execute(this);
    }

    /**
     * 获取请求地址。
     *
     * @return 当前请求地址
     */
    public String getUrl() {
        return url;
    }

    /**
     * 获取 HTTP 方法。
     *
     * @return 当前 HTTP 方法
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * 获取请求头的不可变深层副本。
     *
     * @return 多值请求头
     */
    public Map<String, List<String>> getHeaders() {
        return immutableMultiMap(headers);
    }

    /**
     * 获取查询参数的不可变深层副本。
     *
     * @return 多值查询参数
     */
    public Map<String, List<String>> getQueryParams() {
        return immutableMultiMap(queryParams);
    }

    /**
     * 获取文本请求体。
     *
     * @return 文本请求体
     */
    public String getBody() {
        return body;
    }

    /**
     * 获取二进制请求体的防御性副本。
     *
     * @return 二进制请求体副本
     */
    public byte[] getBodyBytes() {
        return bodyBytes == null ? null : bodyBytes.clone();
    }

    /**
     * 获取本次执行前应调用的拦截器快照。
     *
     * @return 不可变拦截器列表
     */
    List<HttpInterceptor> interceptorSnapshot() {
        return List.copyOf(interceptors);
    }

    /**
     * 校验当前构建状态并生成不可变请求快照。
     *
     * @param config 模板基础配置
     * @return 可重复发送的请求快照
     */
    Snapshot snapshot(HttpConfig config) {
        try {
            URI uri = appendQueryParameters(validateUri(url), queryParams, charset);
            Duration effectiveTimeout = timeout == null ? config.getRequestTimeout() : requirePositive(timeout, "timeout");
            if (maxRetries < 0 || maxRetries > 10) {
                throw new IllegalArgumentException("maxRetries must be between 0 and 10");
            }
            if (retryDelay == null || retryDelay.isNegative()) {
                throw new IllegalArgumentException("retryDelay must not be null or negative");
            }
            for (Integer statusCode : retryOnStatus) {
                if (statusCode == null || statusCode < 100 || statusCode > 599) {
                    throw new IllegalArgumentException("retry status code must be between 100 and 599");
                }
            }
            if (multipart && (body != null || bodyBytes != null)) {
                throw new IllegalArgumentException("multipart body cannot be combined with text or byte body");
            }
            validateNamesAndFiles();
            return new Snapshot(
                    uri,
                    method,
                    immutableMultiMap(headers),
                    body,
                    bodyBytes == null ? null : bodyBytes.clone(),
                    effectiveTimeout,
                    charset,
                    maxRetries,
                    Set.copyOf(retryOnStatus),
                    retryDelay,
                    retryNonIdempotent,
                    multipart,
                    List.copyOf(formFields),
                    List.copyOf(formFiles),
                    multipart ? "letool-" + UUID.randomUUID() : null
            );
        } catch (HttpException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw HttpException.invalidRequest(exception);
        }
    }

    /**
     * 校验请求地址采用 HTTP 或 HTTPS 协议且包含主机。
     *
     * @param value 原始请求地址
     * @return 校验通过的 URI
     */
    private static URI validateUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("url must use http or https and contain a host");
        }
        return uri;
    }

    /**
     * 将多值查询参数追加到原始 URI，并保留片段部分。
     *
     * @param uri 原始 URI
     * @param parameters 多值查询参数
     * @param charset 查询参数字符集
     * @return 带查询参数的新 URI
     */
    private static URI appendQueryParameters(URI uri,
                                             Map<String, List<String>> parameters,
                                             Charset charset) {
        if (parameters.isEmpty()) {
            return uri;
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            requireText(entry.getKey(), "query parameter name");
            for (String value : entry.getValue()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(encode(entry.getKey(), charset))
                        .append('=')
                        .append(encode(value, charset));
            }
        }
        String rawUri = uri.toASCIIString();
        int fragmentIndex = rawUri.indexOf('#');
        String fragment = fragmentIndex < 0 ? "" : rawUri.substring(fragmentIndex);
        String base = fragmentIndex < 0 ? rawUri : rawUri.substring(0, fragmentIndex);
        String rawQuery = uri.getRawQuery();
        String separator = rawQuery == null ? "?" : (rawQuery.isEmpty() ? "" : "&");
        return URI.create(base + separator + query + fragment);
    }

    /**
     * 使用 RFC 3986 兼容的空格表示编码查询参数。
     *
     * @param value 原始参数值
     * @param charset 参数字符集
     * @return 百分号编码结果
     */
    private static String encode(String value, Charset charset) {
        return URLEncoder.encode(value == null ? "" : value, charset).replace("+", "%20");
    }

    /**
     * 校验请求头、Multipart 字段和上传文件。
     */
    private void validateNamesAndFiles() {
        headers.forEach((name, values) -> {
            requireText(name, "header name");
            for (String value : values) {
                requireHeaderValue(value);
            }
        });
        for (FormField field : formFields) {
            requireSafePartName(field.name());
            Objects.requireNonNull(field.value(), "form field value must not be null");
        }
        for (FormFile file : formFiles) {
            requireSafePartName(file.name());
            if (file.path() == null || !Files.isRegularFile(file.path()) || !Files.isReadable(file.path())) {
                throw new IllegalArgumentException("multipart file must be a readable regular file");
            }
            requireSafePartName(file.path().getFileName().toString());
        }
    }

    /**
     * 校验非空白文本。
     *
     * @param value 待校验文本
     * @param name 参数名称
     */
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * 校验请求头值不包含换行符，避免请求头注入。
     *
     * @param value 请求头值
     */
    private static void requireHeaderValue(String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("header value must not contain line breaks");
        }
    }

    /**
     * 校验 Multipart 字段名称可安全写入 Content-Disposition。
     *
     * @param value 字段名称
     */
    private static void requireSafePartName(String value) {
        requireText(value, "multipart part name");
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("multipart part name must not contain line breaks");
        }
    }

    /**
     * 校验持续时间严格大于零。
     *
     * @param value 待校验持续时间
     * @param name 参数名称
     * @return 校验通过的持续时间
     */
    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return value;
    }

    /**
     * 深层复制多值映射并将其转为不可修改结构。
     *
     * @param source 原始多值映射
     * @return 不可修改的深层副本
     */
    private static Map<String, List<String>> immutableMultiMap(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    /**
     * Multipart 文本字段快照。
     *
     * @param name 字段名称
     * @param value 字段值
     */
    record FormField(String name, String value) {
    }

    /**
     * Multipart 文件字段快照。
     *
     * @param name 字段名称
     * @param path 文件路径
     */
    record FormFile(String name, Path path) {
    }

    /**
     * 执行阶段使用的不可变请求快照。
     *
     * @param uri 完整请求 URI
     * @param method HTTP 方法
     * @param headers 多值请求头
     * @param body 文本请求体
     * @param bodyBytes 二进制请求体
     * @param timeout 请求总超时
     * @param charset 文本字符集
     * @param maxRetries 最大重试次数
     * @param retryOnStatus 重试状态码
     * @param retryDelay 重试等待时间
     * @param retryNonIdempotent 是否允许非幂等重试
     * @param multipart 是否使用 Multipart
     * @param formFields Multipart 文本字段
     * @param formFiles Multipart 文件字段
     * @param multipartBoundary Multipart 边界
     */
    record Snapshot(
            URI uri,
            HttpMethod method,
            Map<String, List<String>> headers,
            String body,
            byte[] bodyBytes,
            Duration timeout,
            Charset charset,
            int maxRetries,
            Set<Integer> retryOnStatus,
            Duration retryDelay,
            boolean retryNonIdempotent,
            boolean multipart,
            List<FormField> formFields,
            List<FormFile> formFiles,
            String multipartBoundary) {
    }
}
