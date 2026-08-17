package io.github.leylaragg.letool.pay.model;

/**
 * 查询退款订单的不可变请求。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class RefundQueryRequest {

    private final String provider;
    private final String outRefundNo;

    private RefundQueryRequest(Builder builder) {
        this.provider = PayModelValidator.normalizeProvider(builder.provider);
        this.outRefundNo = PayModelValidator.requireText(builder.outRefundNo, "商户退款单号");
    }

    /** @return 请求构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 商户退款单号 */
    public String getOutRefundNo() { return outRefundNo; }

    /**
     * 退款查询请求构建器。
     */
    public static final class Builder {
        private String provider;
        private String outRefundNo;

        private Builder() { }

        /** @param provider 支付提供方名称
         * @return 当前构建器 */
        public Builder provider(String provider) { this.provider = provider; return this; }

        /** @param outRefundNo 商户退款单号
         * @return 当前构建器 */
        public Builder outRefundNo(String outRefundNo) { this.outRefundNo = outRefundNo; return this; }

        /** @return 退款查询请求 */
        public RefundQueryRequest build() { return new RefundQueryRequest(this); }
    }
}
