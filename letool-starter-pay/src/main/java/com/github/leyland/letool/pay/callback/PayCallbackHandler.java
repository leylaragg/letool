package com.github.leyland.letool.pay.callback;

import com.github.leyland.letool.pay.model.PayChannel;
import com.github.leyland.letool.pay.model.PayResult;

import java.util.Map;

/**
 * 支付回调处理器接口，定义处理支付平台异步通知的标准契约。
 *
 * <p>各业务系统可自定义实现该接口，在收到支付平台回调时执行自定义逻辑，
 * 如更新订单状态、发送通知、触发发货流程等。</p>
 *
 * <p>该接口由 {@link DefaultPayCallbackController} 调用，在验签通过后
 * 将回调参数传递给业务方进行处理。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@FunctionalInterface
public interface PayCallbackHandler {

    // ======================== 回调处理 ========================

    /**
     * 处理支付平台的异步回调通知。
     *
     * <p>此方法在签名验证通过后调用，参数为支付平台回传的原始参数。</p>
     *
     * @param channel 支付渠道，用于区分不同平台的回调
     * @param params  回调请求中的所有参数（key-value 形式）
     * @return 处理后的支付结果
     */
    PayResult handleCallback(PayChannel channel, Map<String, String> params);
}
