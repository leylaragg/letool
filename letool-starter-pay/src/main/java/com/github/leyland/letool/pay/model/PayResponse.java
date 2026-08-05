package com.github.leyland.letool.pay.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付平台操作的标准化不可变响应。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayResponse {

    private final String provider;
    private final String outTradeNo;
    private final String transactionId;
    private final PayStatus status;
    private final PayAction action;
    private final String platformCode;
    private final String platformMessage;
    private final String requestId;
    private final Map<String, String> metadata;

    private PayResponse(Builder builder) {
        this.provider = PayModelValidator.requireText(builder.provider, "支付提供方");
        this.outTradeNo = PayModelValidator.normalizeText(builder.outTradeNo);
        this.transactionId = PayModelValidator.normalizeText(builder.transactionId);
        this.status = PayModelValidator.requireObject(builder.status, "支付状态");
        this.action = builder.action == null ? PayAction.none() : builder.action;
        this.platformCode = PayModelValidator.normalizeText(builder.platformCode);
        this.platformMessage = PayModelValidator.normalizeText(builder.platformMessage);
        this.requestId = PayModelValidator.normalizeText(builder.requestId);
        this.metadata = PayModelValidator.immutableCopy(builder.metadata);
    }

    /** @return 响应构建器 */
    public static Builder builder() { return new Builder(); }

    /** @return 支付提供方名称 */
    public String getProvider() { return provider; }

    /** @return 商户订单号 */
    public String getOutTradeNo() { return outTradeNo; }

    /** @return 平台订单号 */
    public String getTransactionId() { return transactionId; }

    /** @return 标准化支付状态 */
    public PayStatus getStatus() { return status; }

    /** @return 客户端后续动作 */
    public PayAction getAction() { return action; }

    /** @return 平台响应码 */
    public String getPlatformCode() { return platformCode; }

    /** @return 平台响应说明 */
    public String getPlatformMessage() { return platformMessage; }

    /** @return 平台请求标识 */
    public String getRequestId() { return requestId; }

    /** @return 扩展参数的不可变快照 */
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "PayResponse{" + "provider='" + provider + '\'' + ", outTradeNo='" + outTradeNo + '\''
                + ", transactionId='" + transactionId + '\'' + ", status=" + status
                + ", actionType=" + action.getType() + ", metadataCount=" + metadata.size() + '}';
    }

    /**
     * 支付响应构建器。
     */
    public static final class Builder {
        private String provider;
        private String outTradeNo;
        private String transactionId;
        private PayStatus status;
        private PayAction action;
        private String platformCode;
        private String platformMessage;
        private String requestId;
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

        /** @param status 标准化支付状态
         * @return 当前构建器 */
        public Builder status(PayStatus status) { this.status = status; return this; }

        /** @param action 客户端后续动作
         * @return 当前构建器 */
        public Builder action(PayAction action) { this.action = action; return this; }

        /** @param platformCode 平台响应码
         * @return 当前构建器 */
        public Builder platformCode(String platformCode) { this.platformCode = platformCode; return this; }

        /** @param platformMessage 平台响应说明
         * @return 当前构建器 */
        public Builder platformMessage(String platformMessage) { this.platformMessage = platformMessage; return this; }

        /** @param requestId 平台请求标识
         * @return 当前构建器 */
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }

        /**
         * 添加扩展参数。
         *
         * @param name  参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder metadata(String name, String value) { metadata.put(name, value); return this; }

        /** @return 标准化支付响应 */
        public PayResponse build() { return new PayResponse(this); }
    }
}
