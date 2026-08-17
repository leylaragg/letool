package io.github.leylaragg.letool.pay.model;

/**
 * 关闭支付订单的不可变请求。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayCloseRequest {

    private final String provider;
    private final String outTradeNo;
    private final String transactionId;

    private PayCloseRequest(Builder builder) {
        this.provider = PayModelValidator.normalizeProvider(builder.provider);
        this.outTradeNo = PayModelValidator.normalizeText(builder.outTradeNo);
        this.transactionId = PayModelValidator.normalizeText(builder.transactionId);
        PayModelValidator.requirePaymentIdentifier(outTradeNo, transactionId);
    }

    /** @return 请求构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 商户订单号 */
    public String getOutTradeNo() { return outTradeNo; }

    /** @return 平台订单号 */
    public String getTransactionId() { return transactionId; }

    /**
     * 关闭请求构建器。
     */
    public static final class Builder {
        private String provider;
        private String outTradeNo;
        private String transactionId;

        private Builder() { }

        /** @param provider 支付提供方名称
         * @return 当前构建器 */
        public Builder provider(String provider) { this.provider = provider; return this; }

        /** @param outTradeNo 商户订单号
         * @return 当前构建器 */
        public Builder outTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; return this; }

        /** @param transactionId 平台订单号
         * @return 当前构建器 */
        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }

        /** @return 关闭请求 */
        public PayCloseRequest build() { return new PayCloseRequest(this); }
    }
}
