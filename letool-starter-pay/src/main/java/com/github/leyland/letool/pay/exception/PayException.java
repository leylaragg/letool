package com.github.leyland.letool.pay.exception;

import com.github.leyland.letool.pay.model.PayChannel;

/**
 * 支付模块统一异常类，封装支付过程中产生的各种错误信息。
 *
 * <p>相比标准 {@link RuntimeException}，该类额外携带了商户订单号和支付渠道信息，
 * 便于上层在捕获异常后记录日志、返回给前端或触发补偿流程。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * throw new PayException("支付宝支付失败", e);
 * throw new PayException("订单不存在", "ORD-20240601-001", PayChannel.WECHAT);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PayException extends RuntimeException {

    // ======================== 字段 ========================

    /** 商户订单号，关联到具体的支付订单 */
    private final String outTradeNo;

    /** 支付渠道，标识异常发生在哪个支付平台上 */
    private final PayChannel channel;

    // ======================== 构造方法 ========================

    /**
     * 构造一个支付异常（仅包含错误消息）。
     *
     * @param message 错误描述信息
     */
    public PayException(String message) {
        super(message);
        this.outTradeNo = null;
        this.channel = null;
    }

    /**
     * 构造一个支付异常（包含错误消息和原始异常）。
     *
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public PayException(String message, Throwable cause) {
        super(message, cause);
        this.outTradeNo = null;
        this.channel = null;
    }

    /**
     * 构造一个支付异常（包含错误消息、商户订单号和支付渠道）。
     *
     * @param message    错误描述信息
     * @param outTradeNo 商户订单号
     * @param channel    支付渠道
     */
    public PayException(String message, String outTradeNo, PayChannel channel) {
        super(message);
        this.outTradeNo = outTradeNo;
        this.channel = channel;
    }

    /**
     * 构造一个支付异常（包含错误消息、商户订单号、支付渠道和原始异常）。
     *
     * @param message    错误描述信息
     * @param cause      原始异常
     * @param outTradeNo 商户订单号
     * @param channel    支付渠道
     */
    public PayException(String message, Throwable cause, String outTradeNo, PayChannel channel) {
        super(message, cause);
        this.outTradeNo = outTradeNo;
        this.channel = channel;
    }

    // ======================== Getter ========================

    /**
     * 获取商户订单号。
     *
     * @return 商户订单号，可能为 null
     */
    public String getOutTradeNo() {
        return outTradeNo;
    }

    /**
     * 获取支付渠道。
     *
     * @return 支付渠道，可能为 null
     */
    public PayChannel getChannel() {
        return channel;
    }
}
