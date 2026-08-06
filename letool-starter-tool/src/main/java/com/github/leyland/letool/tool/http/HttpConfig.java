package com.github.leyland.letool.tool.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP 客户端的不可变基础配置。
 *
 * <p>配置对象构建完成后不会再发生变化，可以安全地被多个请求和线程共享。请求级超时、重试等行为
 * 由 {@link HttpRequest} 显式声明，避免修改全局可变状态后影响正在执行的请求。</p>
 */
public final class HttpConfig {

    /** 默认 TCP 连接建立超时。 */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 默认单次请求总超时。 */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 默认允许读取到内存的最大响应体字节数。 */
    public static final long DEFAULT_MAX_RESPONSE_BYTES = 16 * 1024 * 1024L;

    /** 单次允许读取到内存的响应体绝对上限，超过此值应改用文件流式下载能力。 */
    public static final long MAX_RESPONSE_BYTES = 256 * 1024 * 1024L;

    /** 可复用的默认配置。 */
    private static final HttpConfig DEFAULTS = builder().build();

    /** TCP 连接建立超时。 */
    private final Duration connectTimeout;

    /** 单次请求总超时。 */
    private final Duration requestTimeout;

    /** 允许读取到内存的最大响应体字节数。 */
    private final long maxResponseBytes;

    /** HTTP 重定向处理策略。 */
    private final HttpClient.Redirect redirectPolicy;

    /**
     * 根据构建器快照创建不可变配置。
     *
     * @param builder 已完成参数收集的配置构建器
     */
    private HttpConfig(Builder builder) {
        this.connectTimeout = requirePositive(builder.connectTimeout, "connectTimeout");
        this.requestTimeout = requirePositive(builder.requestTimeout, "requestTimeout");
        if (builder.maxResponseBytes <= 0 || builder.maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException(
                    "maxResponseBytes must be between 1 and " + MAX_RESPONSE_BYTES);
        }
        this.maxResponseBytes = builder.maxResponseBytes;
        this.redirectPolicy = Objects.requireNonNull(builder.redirectPolicy, "redirectPolicy must not be null");
    }

    /**
     * 获取适合常规业务调用的不可变默认配置。
     *
     * @return 可在线程间复用的默认配置
     */
    public static HttpConfig defaults() {
        return DEFAULTS;
    }

    /**
     * 创建 HTTP 配置构建器。
     *
     * @return 带安全默认值的新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取 TCP 连接建立超时。
     *
     * @return 严格大于零的连接超时
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 获取单次请求总超时。
     *
     * @return 严格大于零的请求超时
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * 获取最大内存响应体字节数。
     *
     * @return 大于零且不超过 {@link #MAX_RESPONSE_BYTES} 的响应体字节上限
     */
    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    /**
     * 获取 HTTP 重定向处理策略。
     *
     * @return JDK HTTP 客户端重定向策略
     */
    public HttpClient.Redirect getRedirectPolicy() {
        return redirectPolicy;
    }

    /**
     * 校验持续时间不为空且严格大于零。
     *
     * @param value 待校验持续时间
     * @param name 配置项名称
     * @return 校验通过的原始持续时间
     */
    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return value;
    }

    /**
     * HTTP 不可变配置构建器。
     */
    public static final class Builder {

        /** TCP 连接建立超时。 */
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

        /** 单次请求总超时。 */
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

        /** 最大内存响应体字节数。 */
        private long maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;

        /** HTTP 重定向处理策略。 */
        private HttpClient.Redirect redirectPolicy = HttpClient.Redirect.NEVER;

        /** 限制外部直接实例化，统一通过 {@link HttpConfig#builder()} 创建。 */
        private Builder() {
        }

        /**
         * 设置 TCP 连接建立超时。
         *
         * @param connectTimeout 严格大于零的连接超时
         * @return 当前构建器
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * 设置单次请求总超时。
         *
         * @param requestTimeout 严格大于零的请求超时
         * @return 当前构建器
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置允许读取到内存的最大响应体字节数。
         *
         * @param maxResponseBytes 大于零且不超过 {@link #MAX_RESPONSE_BYTES} 的字节上限
         * @return 当前构建器
         */
        public Builder maxResponseBytes(long maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
            return this;
        }

        /**
         * 设置 HTTP 重定向处理策略。
         *
         * @param redirectPolicy JDK HTTP 客户端重定向策略
         * @return 当前构建器
         */
        public Builder redirectPolicy(HttpClient.Redirect redirectPolicy) {
            this.redirectPolicy = redirectPolicy;
            return this;
        }

        /**
         * 校验并创建不可变 HTTP 配置。
         *
         * @return 可安全共享的 HTTP 配置
         */
        public HttpConfig build() {
            return new HttpConfig(this);
        }
    }
}
