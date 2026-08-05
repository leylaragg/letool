package com.github.leyland.letool.pay.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByIdRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;

/**
 * 微信支付官方 SDK 服务组合实现。
 */
final class OfficialWechatPaySdk implements WechatPaySdk {

    private final NativePayService nativeService;
    private final H5Service h5Service;
    private final AppServiceExtension appService;
    private final JsapiServiceExtension jsapiService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;

    /**
     * 使用同一官方配置创建全部支付服务和通知解析器。
     *
     * @param config 官方自动证书配置
     */
    OfficialWechatPaySdk(RSAAutoCertificateConfig config) {
        this.nativeService = new NativePayService.Builder().config(config).build();
        this.h5Service = new H5Service.Builder().config(config).build();
        this.appService = new AppServiceExtension.Builder().config(config).build();
        this.jsapiService = new JsapiServiceExtension.Builder().config(config).build();
        this.refundService = new RefundService.Builder().config(config).build();
        this.notificationParser = new NotificationParser(config);
    }

    /** {@inheritDoc} */
    @Override
    public com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse nativePrepay(
            com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request) {
        return nativeService.prepay(request);
    }

    /** {@inheritDoc} */
    @Override
    public com.wechat.pay.java.service.payments.h5.model.PrepayResponse h5Prepay(
            com.wechat.pay.java.service.payments.h5.model.PrepayRequest request) {
        return h5Service.prepay(request);
    }

    /** {@inheritDoc} */
    @Override
    public com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse appPrepay(
            com.wechat.pay.java.service.payments.app.model.PrepayRequest request) {
        return appService.prepayWithRequestPayment(request);
    }

    /** {@inheritDoc} */
    @Override
    public com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse jsapiPrepay(
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request) {
        return jsapiService.prepayWithRequestPayment(request);
    }

    /** {@inheritDoc} */
    @Override
    public Transaction queryByOutTradeNo(QueryOrderByOutTradeNoRequest request) {
        return nativeService.queryOrderByOutTradeNo(request);
    }

    /** {@inheritDoc} */
    @Override
    public Transaction queryById(QueryOrderByIdRequest request) {
        return nativeService.queryOrderById(request);
    }

    /** {@inheritDoc} */
    @Override
    public void close(CloseOrderRequest request) { nativeService.closeOrder(request); }

    /** {@inheritDoc} */
    @Override
    public Refund createRefund(CreateRequest request) { return refundService.create(request); }

    /** {@inheritDoc} */
    @Override
    public Refund queryRefund(QueryByOutRefundNoRequest request) {
        return refundService.queryByOutRefundNo(request);
    }

    /** {@inheritDoc} */
    @Override
    public Transaction parseNotification(RequestParam requestParam) {
        return notificationParser.parse(requestParam, Transaction.class);
    }
}
