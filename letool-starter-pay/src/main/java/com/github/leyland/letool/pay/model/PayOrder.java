package com.github.leyland.letool.pay.model;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付订单模型，封装一笔支付请求所需的所有参数。
 *
 * <p>该对象通过内部 {@link Builder} 类采用建造者模式构建，典型用法如下：</p>
 * <pre>{@code
 * PayOrder order = PayOrder.builder()
 *     .channel(PayChannel.WECHAT)
 *     .outTradeNo("ORD-20240601-001")
 *     .subject("测试商品")
 *     .totalAmount(new BigDecimal("0.01"))
 *     .build();
 *
 * // 可继续注入到 PayTemplate 进行实际支付
 * PayResult result = payTemplate.pay(order);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PayOrder {

    // ======================== 字段 ========================

    /** 商户订单号，在商户系统内唯一 */
    private String outTradeNo;

    /** 商品描述 / 订单标题 */
    private String subject;

    /** 订单总金额（元） */
    private BigDecimal totalAmount;

    /** 货币类型，默认 "CNY"（人民币） */
    private String currency = "CNY";

    /** 支付渠道 */
    private PayChannel channel;

    /** 异步通知地址，为空则使用全局配置 */
    private String notifyUrl;

    /** 支付完成后同步跳转地址（H5 支付场景使用） */
    private String returnUrl;

    /** 扩展参数，用于存放各渠道特有字段 */
    private Map<String, String> extra;

    // ======================== 私有构造 ========================

    private PayOrder() {
    }

    // ======================== Getter ========================

    /**
     * 获取商户订单号。
     *
     * @return 商户订单号
     */
    public String getOutTradeNo() { return outTradeNo; }

    /**
     * 获取商品描述。
     *
     * @return 商品描述 / 订单标题
     */
    public String getSubject() { return subject; }

    /**
     * 获取订单总金额（元）。
     *
     * @return 订单总金额
     */
    public BigDecimal getTotalAmount() { return totalAmount; }

    /**
     * 获取货币类型。
     *
     * @return 货币类型，如 "CNY"
     */
    public String getCurrency() { return currency; }

    /**
     * 获取支付渠道。
     *
     * @return 支付渠道
     */
    public PayChannel getChannel() { return channel; }

    /**
     * 获取异步通知地址。
     *
     * @return 回调通知 URL，可能为 null
     */
    public String getNotifyUrl() { return notifyUrl; }

    /**
     * 获取同步跳转地址。
     *
     * @return 支付完成后的跳转 URL，可能为 null
     */
    public String getReturnUrl() { return returnUrl; }

    /**
     * 获取扩展参数。
     *
     * @return 扩展参数 Map，可能为 null
     */
    public Map<String, String> getExtra() { return extra; }

    // ======================== Builder 入口 ========================

    /**
     * 创建订单构建器。
     *
     * @return 一个新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ======================== 内部 Builder 类 ========================

    /**
     * {@link PayOrder} 的建造者模式实现。
     *
     * <p>所有 setter 方法均返回 Builder 自身，支持链式调用。</p>
     */
    public static class Builder {

        // ===== 内部状态 =====

        private String outTradeNo;
        private String subject;
        private BigDecimal totalAmount;
        private String currency = "CNY";
        private PayChannel channel;
        private String notifyUrl;
        private String returnUrl;
        private Map<String, String> extra;

        /**
         * 设置商户订单号（必填）。
         *
         * @param outTradeNo 商户订单号
         * @return 当前 Builder
         */
        public Builder outTradeNo(String outTradeNo) {
            this.outTradeNo = outTradeNo;
            return this;
        }

        /**
         * 设置商品描述（必填）。
         *
         * @param subject 商品描述 / 订单标题
         * @return 当前 Builder
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * 设置订单总金额（必填，单位为元）。
         *
         * @param totalAmount 订单总金额
         * @return 当前 Builder
         */
        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        /**
         * 设置货币类型（选填，默认 CNY）。
         *
         * @param currency 货币类型，如 "CNY"
         * @return 当前 Builder
         */
        public Builder currency(String currency) {
            this.currency = currency;
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
         * 设置异步通知地址（选填，为空则使用全局配置）。
         *
         * @param notifyUrl 回调通知 URL
         * @return 当前 Builder
         */
        public Builder notifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
            return this;
        }

        /**
         * 设置同步跳转地址（选填，H5 支付场景使用）。
         *
         * @param returnUrl 支付完成后的跳转 URL
         * @return 当前 Builder
         */
        public Builder returnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        /**
         * 添加扩展参数。
         *
         * @param key   参数名
         * @param value 参数值
         * @return 当前 Builder
         */
        public Builder extra(String key, String value) {
            if (this.extra == null) {
                this.extra = new HashMap<>();
            }
            this.extra.put(key, value);
            return this;
        }

        /**
         * 设置扩展参数 Map（会替换已有值）。
         *
         * @param extra 扩展参数 Map
         * @return 当前 Builder
         */
        public Builder extra(Map<String, String> extra) {
            this.extra = extra;
            return this;
        }

        /**
         * 构建支付订单实例。
         *
         * @return 构建完成的 PayOrder 实例
         */
        public PayOrder build() {
            PayOrder order = new PayOrder();
            order.outTradeNo = this.outTradeNo;
            order.subject = this.subject;
            order.totalAmount = this.totalAmount;
            order.currency = this.currency;
            order.channel = this.channel;
            order.notifyUrl = this.notifyUrl;
            order.returnUrl = this.returnUrl;
            order.extra = this.extra;
            return order;
        }
    }
}
