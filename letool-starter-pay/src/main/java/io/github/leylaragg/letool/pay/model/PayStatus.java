package io.github.leylaragg.letool.pay.model;

/**
 * 支付状态枚举，描述一笔支付订单在整个生命周期中的当前状态。
 *
 * <p>状态流转说明：</p>
 * <ul>
 *   <li>{@code WAIT_PAY} — 创建订单后等待用户付款</li>
 *   <li>{@code PAYING} — 用户正在支付中（支付中）</li>
 *   <li>{@code SUCCESS} — 支付成功</li>
 *   <li>{@code CLOSED} — 订单已关闭（超时未支付或被取消）</li>
 *   <li>{@code REFUND} — 已全额退款</li>
 *   <li>{@code REFUND_PROCESSING} — 退款处理中</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum PayStatus {

    /** 等待用户完成支付。 */
    PENDING,

    /** 支付成功。 */
    SUCCESS,

    /** 支付订单已关闭。 */
    CLOSED,

    /** 支付失败。 */
    FAILED,

    /** 支付结果暂时无法确定。 */
    UNKNOWN
}
