package com.github.leyland.letool.pay.model;

import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.exception.PayErrorCode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发起退款的不可变请求。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class RefundRequest {

    private final String provider;
    private final String outTradeNo;
    private final String transactionId;
    private final String outRefundNo;
    private final BigDecimal amount;
    private final BigDecimal totalAmount;
    private final String currency;
    private final String reason;
    private final String notifyUrl;
    private final Map<String, String> metadata;

    private RefundRequest(Builder builder) {
        this.provider = PayModelValidator.normalizeProvider(builder.provider);
        this.outTradeNo = PayModelValidator.normalizeText(builder.outTradeNo);
        this.transactionId = PayModelValidator.normalizeText(builder.transactionId);
        PayModelValidator.requirePaymentIdentifier(outTradeNo, transactionId);
        this.outRefundNo = PayModelValidator.requireText(builder.outRefundNo, "商户退款单号");
        this.amount = PayModelValidator.requireAmount(builder.amount, "退款金额");
        this.totalAmount = builder.totalAmount == null ? null
                : PayModelValidator.requireAmount(builder.totalAmount, "原订单金额");
        if (totalAmount != null && amount.compareTo(totalAmount) > 0) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "退款金额不能大于原订单金额");
        }
        this.currency = PayModelValidator.requireCny(builder.currency);
        this.reason = PayModelValidator.normalizeText(builder.reason);
        this.notifyUrl = PayModelValidator.normalizeText(builder.notifyUrl);
        this.metadata = PayModelValidator.immutableCopy(builder.metadata);
    }

    /** @return 请求构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 商户订单号 */
    public String getOutTradeNo() { return outTradeNo; }

    /** @return 平台订单号 */
    public String getTransactionId() { return transactionId; }

    /** @return 商户退款单号 */
    public String getOutRefundNo() { return outRefundNo; }

    /** @return 退款金额 */
    public BigDecimal getAmount() { return amount; }

    /** @return 原订单金额，未指定时返回 {@code null} */
    public BigDecimal getTotalAmount() { return totalAmount; }

    /** @return 币种代码 */
    public String getCurrency() { return currency; }

    /** @return 退款原因 */
    public String getReason() { return reason; }

    /** @return 退款通知地址 */
    public String getNotifyUrl() { return notifyUrl; }

    /** @return 扩展参数的不可变快照 */
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "RefundRequest{" + "provider='" + provider + '\'' + ", outTradeNo='" + outTradeNo + '\''
                + ", outRefundNo='" + outRefundNo + '\'' + ", amount=" + amount
                + ", currency='" + currency + '\'' + ", metadataCount=" + metadata.size() + '}';
    }

    /**
     * 退款请求构建器。
     */
    public static final class Builder {
        private String provider;
        private String outTradeNo;
        private String transactionId;
        private String outRefundNo;
        private BigDecimal amount;
        private BigDecimal totalAmount;
        private String currency = "CNY";
        private String reason;
        private String notifyUrl;
        private final Map<String, String> metadata = new LinkedHashMap<>();

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

        /** @param outRefundNo 商户退款单号
         * @return 当前构建器 */
        public Builder outRefundNo(String outRefundNo) { this.outRefundNo = outRefundNo; return this; }

        /** @param amount 退款金额
         * @return 当前构建器 */
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }

        /** @param totalAmount 原订单金额
         * @return 当前构建器 */
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }

        /** @param currency 币种代码
         * @return 当前构建器 */
        public Builder currency(String currency) { this.currency = currency; return this; }

        /** @param reason 退款原因
         * @return 当前构建器 */
        public Builder reason(String reason) { this.reason = reason; return this; }

        /** @param notifyUrl 退款通知地址
         * @return 当前构建器 */
        public Builder notifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; return this; }

        /**
         * 添加扩展参数。
         *
         * @param name  参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder metadata(String name, String value) { metadata.put(name, value); return this; }

        /** @return 退款请求 */
        public RefundRequest build() { return new RefundRequest(this); }
    }
}
