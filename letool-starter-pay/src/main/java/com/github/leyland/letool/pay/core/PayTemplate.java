package com.github.leyland.letool.pay.core;

import com.github.leyland.letool.pay.config.PayProperties;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayChannel;
import com.github.leyland.letool.pay.model.PayOrder;
import com.github.leyland.letool.pay.model.PayResult;
import com.github.leyland.letool.pay.model.RefundOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付模板类 —— 支付模块的统一入口，对上层业务屏蔽各支付渠道的差异。
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>根据支付订单中的 {@link PayChannel} 路由到对应的 {@link PayProvider}</li>
 *   <li>统一的支付、查询、退款、回调处理 API</li>
 *   <li>回调签名验证（由各 Provider 实现具体的验签算法）</li>
 * </ul>
 *
 * <p><b>典型用法：</b></p>
 * <pre>{@code
 * // 1. 构建支付订单
 * PayOrder order = PayOrder.builder()
 *     .channel(PayChannel.WECHAT)
 *     .outTradeNo("ORD-" + System.currentTimeMillis())
 *     .subject("测试商品")
 *     .totalAmount(new BigDecimal("0.01"))
 *     .build();
 *
 * // 2. 发起支付
 * PayResult result = payTemplate.pay(order);
 *
 * // 3. 查询订单
 * PayResult queryResult = payTemplate.query("ORD-xxx", PayChannel.WECHAT);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PayTemplate {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(PayTemplate.class);

    // ======================== 字段 ========================

    /** 支付提供者注册表，key 为提供者名称（大写），value 为对应实现 */
    private final Map<String, PayProvider> providerMap;

    /** 支付模块配置属性 */
    private final PayProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 构造支付模板。
     *
     * <p>接收所有注册的 {@link PayProvider} 实例并索引到内部 Map 中，
     * 后续根据 {@link PayChannel} 路由到对应实现。</p>
     *
     * @param providers  支付提供者 Map（key 为提供者名称）
     * @param properties 支付模块配置属性
     */
    public PayTemplate(Map<String, PayProvider> providers, PayProperties properties) {
        this.providerMap = new ConcurrentHashMap<>(providers);
        this.properties = properties;
        log.info("PayTemplate initialized with providers: {}", providerMap.keySet());
    }

    // ======================== 支付操作 ========================

    /**
     * 发起支付。
     *
     * <p>根据订单中的 {@code channel} 路由到对应的支付提供者。
     * 若未找到对应渠道的实现，将抛出 {@link PayException}。</p>
     *
     * @param order 支付订单
     * @return 支付结果
     * @throws PayException 当支付渠道不支持或支付失败时抛出
     */
    public PayResult pay(PayOrder order) {
        PayProvider provider = getProvider(order.getChannel());
        log.info("PayTemplate.pay → channel={}, outTradeNo={}, amount={}",
                order.getChannel(), order.getOutTradeNo(), order.getTotalAmount());
        return provider.pay(order);
    }

    /**
     * 查询订单支付状态。
     *
     * <p>根据商户订单号和支付渠道查询订单的当前状态。</p>
     *
     * @param outTradeNo 商户订单号
     * @param channel    支付渠道
     * @return 订单状态查询结果
     * @throws PayException 当支付渠道不支持时抛出
     */
    public PayResult query(String outTradeNo, PayChannel channel) {
        PayProvider provider = getProvider(channel);
        log.info("PayTemplate.query → channel={}, outTradeNo={}", channel, outTradeNo);
        return provider.query(outTradeNo);
    }

    // ======================== 退款操作 ========================

    /**
     * 发起退款。
     *
     * <p>根据退款订单中的 {@code channel} 路由到对应的支付提供者。</p>
     *
     * @param refundOrder 退款订单
     * @return 退款结果
     * @throws PayException 当支付渠道不支持或退款失败时抛出
     */
    public PayResult refund(RefundOrder refundOrder) {
        PayProvider provider = getProvider(refundOrder.getChannel());
        log.info("PayTemplate.refund → channel={}, outTradeNo={}, refundNo={}, amount={}",
                refundOrder.getChannel(), refundOrder.getOutTradeNo(),
                refundOrder.getOutRefundNo(), refundOrder.getRefundAmount());
        return provider.refund(refundOrder);
    }

    // ======================== 回调处理 ========================

    /**
     * 处理支付平台的异步回调通知。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据渠道获取对应的 Provider</li>
     *   <li>若配置要求验签（{@code letool.pay.verifySign=true}），
     *       则从参数中提取 sign 字段并调用 Provider 的验签方法</li>
     *   <li>验签通过后将原始参数传递给上层处理</li>
     * </ol>
     *
     * @param channel 支付渠道
     * @param params  回调请求参数（所有参数以 key-value 形式传入）
     * @return 解析后的支付结果
     * @throws PayException 当验签失败或渠道不支持时抛出
     */
    public PayResult handleCallback(PayChannel channel, Map<String, String> params) {
        PayProvider provider = getProvider(channel);
        log.info("PayTemplate.handleCallback → channel={}, params={}", channel, params);

        // 验签
        if (properties.isVerifySign()) {
            String sign = params.get("sign");
            if (sign == null || sign.isEmpty()) {
                log.warn("Callback sign is empty, channel={}", channel);
                throw new PayException("回调签名缺失", null, channel);
            }
            boolean verified = provider.verifySign(params, sign);
            if (!verified) {
                log.error("Callback sign verification failed, channel={}", channel);
                throw new PayException("回调签名验证失败", null, channel);
            }
            log.info("Callback sign verified successfully, channel={}", channel);
        }

        // 返回包含原始参数的支付结果，由上层业务自行解析
        return PayResult.fromCallback(Collections.unmodifiableMap(params));
    }

    // ======================== 私有方法 ========================

    /**
     * 根据支付渠道获取对应的提供者实现。
     *
     * @param channel 支付渠道枚举
     * @return 对应的 PayProvider 实例
     * @throws PayException 当未注册对应渠道的 Provider 时抛出
     */
    private PayProvider getProvider(PayChannel channel) {
        if (channel == null) {
            throw new PayException("支付渠道不能为空");
        }
        PayProvider provider = providerMap.get(channel.name());
        if (provider == null) {
            throw new PayException("不支持的支付渠道: " + channel.name(), null, channel);
        }
        return provider;
    }

    /**
     * 获取已注册的支付提供者数量。
     *
     * @return 提供者数量
     */
    public int getProviderCount() {
        return providerMap.size();
    }
}
