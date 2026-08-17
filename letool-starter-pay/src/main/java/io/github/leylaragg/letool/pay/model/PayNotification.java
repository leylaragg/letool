package io.github.leylaragg.letool.pay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 完成验签和解密后的标准化支付通知。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayNotification {

    private final String provider;
    private final String eventType;
    private final String outTradeNo;
    private final String transactionId;
    private final BigDecimal amount;
    private final String currency;
    private final PayStatus status;
    private final Instant completedAt;
    private final String notificationId;
    private final Map<String, String> extensions;

    private PayNotification(Builder builder) {
        this.provider = PayModelValidator.requireText(builder.provider, "支付提供方");
        this.eventType = PayModelValidator.requireText(builder.eventType, "通知事件类型");
        this.outTradeNo = PayModelValidator.requireText(builder.outTradeNo, "商户订单号");
        this.transactionId = PayModelValidator.normalizeText(builder.transactionId);
        this.amount = builder.amount == null ? null : PayModelValidator.requireAmount(builder.amount, "支付金额");
        this.currency = PayModelValidator.requireCny(builder.currency);
        this.status = PayModelValidator.requireObject(builder.status, "支付状态");
        this.completedAt = builder.completedAt;
        this.notificationId = PayModelValidator.normalizeText(builder.notificationId);
        this.extensions = PayModelValidator.immutableCopy(builder.extensions);
    }

    /** @return 通知构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 通知事件类型 */
    public String getEventType() { return eventType; }

    /** @return 商户订单号 */
    public String getOutTradeNo() { return outTradeNo; }

    /** @return 平台订单号 */
    public String getTransactionId() { return transactionId; }

    /** @return 支付金额 */
    public BigDecimal getAmount() { return amount; }

    /** @return 币种代码 */
    public String getCurrency() { return currency; }

    /** @return 标准化支付状态 */
    public PayStatus getStatus() { return status; }

    /** @return 支付完成时间 */
    public Instant getCompletedAt() { return completedAt; }

    /** @return 平台通知标识 */
    public String getNotificationId() { return notificationId; }

    /** @return 扩展字段的不可变快照 */
    public Map<String, String> getExtensions() { return extensions; }

    @Override
    public String toString() {
        return "PayNotification{" + "provider='" + provider + '\'' + ", eventType='" + eventType + '\''
                + ", outTradeNo='" + outTradeNo + '\'' + ", transactionId='" + transactionId + '\''
                + ", status=" + status + ", extensionCount=" + extensions.size() + '}';
    }

    /**
     * 标准化支付通知构建器。
     */
    public static final class Builder {
        private String provider;
        private String eventType;
        private String outTradeNo;
        private String transactionId;
        private BigDecimal amount;
        private String currency = "CNY";
        private PayStatus status;
        private Instant completedAt;
        private String notificationId;
        private final Map<String, String> extensions = new LinkedHashMap<>();

        private Builder() { }

        /** @param provider 支付提供方名称
         * @return 当前构建器 */
        public Builder provider(String provider) { this.provider = provider; return this; }

        /** @param eventType 通知事件类型
         * @return 当前构建器 */
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }

        /** @param outTradeNo 商户订单号
         * @return 当前构建器 */
        public Builder outTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; return this; }

        /** @param transactionId 平台订单号
         * @return 当前构建器 */
        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }

        /** @param amount 支付金额
         * @return 当前构建器 */
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }

        /** @param currency 币种代码
         * @return 当前构建器 */
        public Builder currency(String currency) { this.currency = currency; return this; }

        /** @param status 标准化支付状态
         * @return 当前构建器 */
        public Builder status(PayStatus status) { this.status = status; return this; }

        /** @param completedAt 支付完成时间
         * @return 当前构建器 */
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }

        /** @param notificationId 平台通知标识
         * @return 当前构建器 */
        public Builder notificationId(String notificationId) { this.notificationId = notificationId; return this; }

        /** @param name 扩展字段名称
         * @param value 扩展字段值
         * @return 当前构建器 */
        public Builder extension(String name, String value) { extensions.put(name, value); return this; }

        /** @return 标准化支付通知 */
        public PayNotification build() { return new PayNotification(this); }
    }
}
