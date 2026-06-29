package com.github.leyland.letool.pay.core;

import com.github.leyland.letool.pay.model.PayOrder;
import com.github.leyland.letool.pay.model.PayResult;
import com.github.leyland.letool.pay.model.RefundOrder;

import java.util.Map;

/**
 * 支付提供者接口，定义各支付渠道必须实现的标准契约。
 *
 * <p>每种支付渠道（支付宝、微信支付、银联等）都应提供该接口的实现。
 * 上层 {@link PayTemplate} 通过此接口与具体渠道解耦，实现统一路由和调用。</p>
 *
 * <p>接口方法说明：</p>
 * <ul>
 *   <li>{@link #pay(PayOrder)} — 发起支付下单</li>
 *   <li>{@link #query(String)} — 查询订单状态</li>
 *   <li>{@link #refund(RefundOrder)} — 发起退款</li>
 *   <li>{@link #queryRefund(String)} — 查询退款状态</li>
 *   <li>{@link #verifySign(Map, String)} — 验证回调签名的合法性</li>
 *   <li>{@link #getProviderName()} — 返回提供者标识名称</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface PayProvider {

    // ======================== 支付操作 ========================

    /**
     * 发起支付下单。
     *
     * <p>根据订单信息构造支付请求并发送至支付平台，返回支付结果。
     * 对于需要用户跳转的场景（如扫码支付、H5 支付），结果中会包含支付 URL。</p>
     *
     * @param order 支付订单，包含金额、商品描述、回调地址等信息
     * @return 支付结果，包含交易流水号、状态、以及各渠道的原始响应数据
     */
    PayResult pay(PayOrder order);

    /**
     * 查询订单支付状态。
     *
     * <p>通过商户订单号向支付平台查询该订单的当前状态（待支付 / 已支付 / 已关闭等）。</p>
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果，包含订单状态和交易详情
     */
    PayResult query(String outTradeNo);

    // ======================== 退款操作 ========================

    /**
     * 发起退款。
     *
     * <p>对已支付的订单进行退款操作。退款金额不能超过原订单金额。
     * 部分渠道支持部分退款，具体由渠道实现决定。</p>
     *
     * @param refundOrder 退款订单，包含原订单号、退款金额、退款原因等
     * @return 退款结果
     */
    PayResult refund(RefundOrder refundOrder);

    /**
     * 查询退款状态。
     *
     * <p>通过退款单号向支付平台查询该退款的处理进度和结果。</p>
     *
     * @param refundNo 退款单号
     * @return 退款查询结果
     */
    PayResult queryRefund(String refundNo);

    // ======================== 安全验证 ========================

    /**
     * 验证支付平台回调请求的签名。
     *
     * <p>支付平台在发起异步通知时会附带数字签名，调用方应使用此方法
     * 校验签名的合法性，防止伪造通知。校验过程因渠道而异（RSA2、HMAC-SHA256 等）。</p>
     *
     * @param params 回调请求中的所有参数（key-value 形式）
     * @param sign   支付平台回传的签名字符串
     * @return {@code true} 签名验证通过，{@code false} 签名无效
     */
    boolean verifySign(Map<String, String> params, String sign);

    // ======================== 元数据 ========================

    /**
     * 获取支付提供者的标识名称。
     *
     * <p>该名称用于日志输出、路由匹配以及 {@link PayTemplate} 内部的 Provider Map 索引。
     * 命名建议使用渠道英文名称（如 "ALIPAY"、"WECHAT"、"UNION"）。</p>
     *
     * @return 提供者名称
     */
    String getProviderName();
}
