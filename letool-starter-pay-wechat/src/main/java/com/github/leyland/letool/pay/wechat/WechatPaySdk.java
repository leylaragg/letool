package com.github.leyland.letool.pay.wechat;

import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByIdRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;

/**
 * 隔离微信支付官方服务对象的内部调用边界。
 */
interface WechatPaySdk {

    /** @param request Native 下单请求
     * @return Native 下单响应 */
    com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse nativePrepay(
            com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request);

    /** @param request H5 下单请求
     * @return H5 下单响应 */
    com.wechat.pay.java.service.payments.h5.model.PrepayResponse h5Prepay(
            com.wechat.pay.java.service.payments.h5.model.PrepayRequest request);

    /** @param request APP 下单请求
     * @return 带签名 APP 参数的响应 */
    com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse appPrepay(
            com.wechat.pay.java.service.payments.app.model.PrepayRequest request);

    /** @param request JSAPI 下单请求
     * @return 带签名 JSAPI 参数的响应 */
    com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse jsapiPrepay(
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request);

    /** @param request 商户订单号查询请求
     * @return 微信支付订单 */
    Transaction queryByOutTradeNo(QueryOrderByOutTradeNoRequest request);

    /** @param request 微信订单号查询请求
     * @return 微信支付订单 */
    Transaction queryById(QueryOrderByIdRequest request);

    /** @param request 关单请求 */
    void close(CloseOrderRequest request);

    /** @param request 退款请求
     * @return 微信退款单 */
    Refund createRefund(CreateRequest request);

    /** @param request 退款查询请求
     * @return 微信退款单 */
    Refund queryRefund(QueryByOutRefundNoRequest request);

    /** @param requestParam 原始通知验签参数
     * @return 验签解密后的微信订单 */
    Transaction parseNotification(RequestParam requestParam);
}
