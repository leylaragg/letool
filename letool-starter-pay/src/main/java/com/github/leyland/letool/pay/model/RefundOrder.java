package com.github.leyland.letool.pay.model;

import java.math.BigDecimal;

/**
 * 退款订单模型，封装一笔退款请求所需的所有参数。
 *
 * <p>该对象通过内部 {@link Builder} 类采用建造者模式构建，典型用法如下：</p>
 * <pre>{@code
 * RefundOrder refund = RefundOrder.builder()
 *     .outTradeNo("ORD-20240601-001")
 *     .outRefundNo("RFD-20240601-001")
 *     .refundAmount(new BigDecimal("0.01"))
 *     .refundReason("用户申请退款")
 *     .channel(PayChannel.ALIPAY)
 *     .build();
 *
 * PayResult result = payTemplate.refund(refund);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class RefundOrder {

    // ======================== 字段 ========================

    /** 原支付订单的商户订单号 */
    private String outTradeNo;

    /** 退款单号，商户系统内唯一 */
    private String outRefundNo;

    /** 退款金额（元），不能大于原订单金额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /** 支付渠道 */
    private PayChannel channel;

    // ======================== 私有构造 ========================

    private RefundOrder() {
    }

    // ======================== Getter ========================

    /**
     * 获取原支付订单的商户订单号。
     *
     * @return 原订单号
     */
    public String getOutTradeNo() { return outTradeNo; }

    /**
     * 获取退款单号。
     *
     * @return 退款单号
     */
    public String getOutRefundNo() { return outRefundNo; }

    /**
     * 获取退款金额（元）。
     *
     * @return 退款金额
     */
    public BigDecimal getRefundAmount() { return refundAmount; }

    /**
     * 获取退款原因。
     *
     * @return 退款原因，可能为 null
     */
    public String getRefundReason() { return refundReason; }

    /**
     * 获取支付渠道。
     *
     * @return 支付渠道
     */
    public PayChannel getChannel() { return channel; }

    // ======================== Builder 入口 ========================

    /**
     * 创建退款订单构建器。
     *
     * @return 一个新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ======================== 内部 Builder 类 ========================

    /**
     * {@link RefundOrder} 的建造者模式实现。
     *
     * <p>所有 setter 方法均返回 Builder 自身，支持链式调用。</p>
     */
    public static class Builder {

        private String outTradeNo;
        private String outRefundNo;
        private BigDecimal refundAmount;
        private String refundReason;
        private PayChannel channel;

        /**
         * 设置原支付订单号（必填）。
         *
         * @param outTradeNo 原商户订单号
         * @return 当前 Builder
         */
        public Builder outTradeNo(String outTradeNo) {
            this.outTradeNo = outTradeNo;
            return this;
        }

        /**
         * 设置退款单号（必填）。
         *
         * @param outRefundNo 商户退款单号
         * @return 当前 Builder
         */
        public Builder outRefundNo(String outRefundNo) {
            this.outRefundNo = outRefundNo;
            return this;
        }

        /**
         * 设置退款金额（必填，单位为元）。
         *
         * @param refundAmount 退款金额
         * @return 当前 Builder
         */
        public Builder refundAmount(BigDecimal refundAmount) {
            this.refundAmount = refundAmount;
            return this;
        }

        /**
         * 设置退款原因（选填）。
         *
         * @param refundReason 退款原因
         * @return 当前 Builder
         */
        public Builder refundReason(String refundReason) {
            this.refundReason = refundReason;
            return this;
        }

        /**
         * 设置支付渠道（必填）。
         *
         * @param channel 支付渠道
         * @return 当前 Builder
         */
        public Builder channel(PayChannel channel) {
            this.channel = channel;
            return this;
        }

        /**
         * 构建退款订单实例。
         *
         * @return 构建完成的 RefundOrder 实例
         */
        public RefundOrder build() {
            RefundOrder order = new RefundOrder();
            order.outTradeNo = this.outTradeNo;
            order.outRefundNo = this.outRefundNo;
            order.refundAmount = this.refundAmount;
            order.refundReason = this.refundReason;
            order.channel = this.channel;
            return order;
        }
    }
}
