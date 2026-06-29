package com.github.leyland.letool.pay.model;

/**
 * 支付渠道枚举，定义平台支持的所有支付服务提供商。
 *
 * <p>在发起支付或查询时，通过该枚举指定目标支付渠道，{@code PayTemplate}
 * 将根据渠道名称路由到对应的 {@code PayProvider} 实现。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * PayChannel channel = PayChannel.ALIPAY;
 * String code = channel.name();       // "ALIPAY"
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum PayChannel {

    // ======================== 枚举常量 ========================

    /** 支付宝 */
    ALIPAY,

    /** 微信支付 */
    WECHAT,

    /** 银联支付 */
    UNION,

    /** Mock 模拟支付，用于开发测试环境 */
    MOCK
}
