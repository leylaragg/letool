package com.github.leyland.letool.pay.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 支付平台回调的原始不可变请求。
 *
 * <p>该对象只负责完整传递验签所需的原始数据，不会提前解析或改写正文。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayNotificationRequest {

    private final String provider;
    private final String rawBody;
    private final Map<String, String> headers;
    private final Map<String, String> formParameters;

    private PayNotificationRequest(Builder builder) {
        this.provider = PayModelValidator.requireText(builder.provider, "支付提供方")
                .toLowerCase(Locale.ROOT);
        this.rawBody = builder.rawBody;
        this.headers = immutableHeaders(builder.headers);
        this.formParameters = PayModelValidator.immutableCopy(builder.formParameters);
    }

    /** @return 请求构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 未改写的回调正文 */
    public String getRawBody() { return rawBody; }

    /** @return 小写键名的不可变请求头快照 */
    public Map<String, String> getHeaders() { return headers; }

    /**
     * 以大小写不敏感方式获取请求头。
     *
     * @param name 请求头名称
     * @return 请求头值，不存在时返回 {@code null}
     */
    public String getHeader(String name) {
        return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return 不可变表单参数快照 */
    public Map<String, String> getFormParameters() { return formParameters; }

    @Override
    public String toString() {
        return "PayNotificationRequest{" + "provider='" + provider + '\''
                + ", rawBodyLength=" + (rawBody == null ? 0 : rawBody.length())
                + ", headerCount=" + headers.size() + ", formParameterCount=" + formParameters.size() + '}';
    }

    private static Map<String, String> immutableHeaders(Map<String, String> source) {
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> copy.put(
                PayModelValidator.requireText(name, "请求头名称").toLowerCase(Locale.ROOT),
                PayModelValidator.requireText(value, "请求头值")));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 回调原始请求构建器。
     */
    public static final class Builder {
        private String provider;
        private String rawBody;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> formParameters = new LinkedHashMap<>();

        private Builder() { }

        /** @param provider 支付提供方名称
         * @return 当前构建器 */
        public Builder provider(String provider) { this.provider = provider; return this; }

        /** @param rawBody 未改写的回调正文
         * @return 当前构建器 */
        public Builder rawBody(String rawBody) { this.rawBody = rawBody; return this; }

        /**
         * 添加请求头。
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder header(String name, String value) { headers.put(name, value); return this; }

        /**
         * 添加表单参数。
         *
         * @param name  参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder formParameter(String name, String value) { formParameters.put(name, value); return this; }

        /** @return 回调原始请求 */
        public PayNotificationRequest build() { return new PayNotificationRequest(this); }
    }
}
