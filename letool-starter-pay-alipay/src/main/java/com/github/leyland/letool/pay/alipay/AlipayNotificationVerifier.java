package com.github.leyland.letool.pay.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;

import java.util.Map;

/**
 * 支付宝通知验签策略。
 *
 * <p>独立策略用于隔离静态官方 SDK API，生产默认实现不可关闭。</p>
 */
@FunctionalInterface
interface AlipayNotificationVerifier {

    /**
     * 验证支付宝通知签名。
     *
     * @param parameters 完整通知参数
     * @param properties 支付宝配置
     * @return 验签是否通过
     * @throws AlipayApiException 官方 SDK 验签异常
     */
    boolean verify(Map<String, String> parameters, AlipayPayProperties properties)
            throws AlipayApiException;

    /**
     * 创建使用支付宝官方 SDK 的验签策略。
     *
     * @return 官方验签策略
     */
    static AlipayNotificationVerifier official() {
        return (parameters, properties) -> AlipaySignature.rsaCheckV1(
                parameters,
                properties.getAlipayPublicKey(),
                properties.getCharset(),
                properties.getSignType());
    }
}
