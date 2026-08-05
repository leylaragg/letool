package com.github.leyland.letool.pay.model;

import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.exception.PayErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创建支付订单的不可变请求。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayRequest {

    private final String provider;
    private final PayScene scene;
    private final String outTradeNo;
    private final String subject;
    private final BigDecimal amount;
    private final String currency;
    private final String notifyUrl;
    private final String returnUrl;
    private final String payerId;
    private final String clientIp;
    private final Instant expiresAt;
    private final Map<String, String> metadata;

    private PayRequest(Builder builder) {
        this.provider = PayModelValidator.normalizeProvider(builder.provider);
        this.scene = PayModelValidator.requireObject(builder.scene, "支付场景");
        this.outTradeNo = PayModelValidator.requireText(builder.outTradeNo, "商户订单号");
        this.subject = PayModelValidator.requireText(builder.subject, "订单标题");
        this.amount = PayModelValidator.requireAmount(builder.amount, "支付金额");
        this.currency = PayModelValidator.requireCny(builder.currency);
        this.notifyUrl = PayModelValidator.normalizeText(builder.notifyUrl);
        this.returnUrl = PayModelValidator.normalizeText(builder.returnUrl);
        this.payerId = PayModelValidator.normalizeText(builder.payerId);
        this.clientIp = PayModelValidator.normalizeText(builder.clientIp);
        this.expiresAt = builder.expiresAt;
        this.metadata = PayModelValidator.immutableCopy(builder.metadata);
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "支付过期时间必须晚于当前时间");
        }
    }

    /**
     * 创建请求构建器。
     *
     * @return 请求构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 支付提供方名称，未指定时返回 {@code null} */
    public String getProvider() { return provider; }

    /** @return 支付场景 */
    public PayScene getScene() { return scene; }

    /** @return 商户订单号 */
    public String getOutTradeNo() { return outTradeNo; }

    /** @return 订单标题 */
    public String getSubject() { return subject; }

    /** @return 支付金额 */
    public BigDecimal getAmount() { return amount; }

    /** @return 币种代码 */
    public String getCurrency() { return currency; }

    /** @return 异步通知地址 */
    public String getNotifyUrl() { return notifyUrl; }

    /** @return 同步返回地址 */
    public String getReturnUrl() { return returnUrl; }

    /** @return 付款方标识 */
    public String getPayerId() { return payerId; }

    /** @return 客户端 IP 地址 */
    public String getClientIp() { return clientIp; }

    /** @return 支付过期时间 */
    public Instant getExpiresAt() { return expiresAt; }

    /** @return 扩展参数的不可变快照 */
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "PayRequest{" + "provider='" + provider + '\'' + ", scene=" + scene
                + ", outTradeNo='" + outTradeNo + '\'' + ", amount=" + amount
                + ", currency='" + currency + '\'' + ", metadataCount=" + metadata.size() + '}';
    }

    /**
     * 支付请求构建器。
     */
    public static final class Builder {

        private String provider;
        private PayScene scene;
        private String outTradeNo;
        private String subject;
        private BigDecimal amount;
        private String currency = "CNY";
        private String notifyUrl;
        private String returnUrl;
        private String payerId;
        private String clientIp;
        private Instant expiresAt;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder() {
        }

        /** @param provider 支付提供方名称
         * @return 当前构建器 */
        public Builder provider(String provider) { this.provider = provider; return this; }

        /** @param scene 支付场景
         * @return 当前构建器 */
        public Builder scene(PayScene scene) { this.scene = scene; return this; }

        /** @param outTradeNo 商户订单号
         * @return 当前构建器 */
        public Builder outTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; return this; }

        /** @param subject 订单标题
         * @return 当前构建器 */
        public Builder subject(String subject) { this.subject = subject; return this; }

        /** @param amount 支付金额
         * @return 当前构建器 */
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }

        /** @param currency 币种代码
         * @return 当前构建器 */
        public Builder currency(String currency) { this.currency = currency; return this; }

        /** @param notifyUrl 异步通知地址
         * @return 当前构建器 */
        public Builder notifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; return this; }

        /** @param returnUrl 同步返回地址
         * @return 当前构建器 */
        public Builder returnUrl(String returnUrl) { this.returnUrl = returnUrl; return this; }

        /** @param payerId 付款方标识
         * @return 当前构建器 */
        public Builder payerId(String payerId) { this.payerId = payerId; return this; }

        /** @param clientIp 客户端 IP 地址
         * @return 当前构建器 */
        public Builder clientIp(String clientIp) { this.clientIp = clientIp; return this; }

        /** @param expiresAt 支付过期时间
         * @return 当前构建器 */
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }

        /**
         * 添加扩展参数。
         *
         * @param name  参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder metadata(String name, String value) {
            metadata.put(name, value);
            return this;
        }

        /**
         * 创建不可变支付请求。
         *
         * @return 支付请求
         */
        public PayRequest build() { return new PayRequest(this); }
    }
}
