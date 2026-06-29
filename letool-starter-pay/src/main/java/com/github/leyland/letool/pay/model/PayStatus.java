package com.github.leyland.letool.pay.model;

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

    // ======================== 枚举常量 ========================

    /** 等待支付 — 订单已创建但用户尚未付款 */
    WAIT_PAY,

    /** 支付中 — 用户已发起支付，等待支付平台确认 */
    PAYING,

    /** 支付成功 — 资金已到账 */
    SUCCESS,

    /** 已关闭 — 订单超时或被主动取消 */
    CLOSED,

    /** 已退款 — 全额退款完成 */
    REFUND,

    /** 退款处理中 — 退款已发起但尚未到账 */
    REFUND_PROCESSING
}
