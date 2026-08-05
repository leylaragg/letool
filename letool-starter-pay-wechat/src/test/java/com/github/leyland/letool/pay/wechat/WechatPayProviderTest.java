package com.github.leyland.letool.pay.wechat;

import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayActionType;
import com.github.leyland.letool.pay.model.PayCloseRequest;
import com.github.leyland.letool.pay.model.PayNotificationRequest;
import com.github.leyland.letool.pay.model.PayQueryRequest;
import com.github.leyland.letool.pay.model.PayRequest;
import com.github.leyland.letool.pay.model.PayScene;
import com.github.leyland.letool.pay.model.PayStatus;
import com.github.leyland.letool.pay.model.RefundQueryRequest;
import com.github.leyland.letool.pay.model.RefundRequest;
import com.github.leyland.letool.pay.model.RefundStatus;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WechatPayProvider} 微信支付 V3 官方 SDK 适配契约测试。
 */
class WechatPayProviderTest {

    private WechatPaySdk sdk;
    private WechatPayProvider provider;

    /**
     * 初始化测试 Provider。
     */
    @BeforeEach
    void setUp() {
        sdk = mock(WechatPaySdk.class);
        WechatPayProperties properties = new WechatPayProperties();
        properties.setAppId("wx-app");
        properties.setMchId("merchant");
        properties.setNotifyUrl("https://example.com/wechat/notify");
        properties.setH5AppName("Test App");
        properties.setH5AppUrl("https://example.com");
        provider = new WechatPayProvider(sdk, properties);
    }

    /**
     * 验证 Native、H5、APP 和 JSAPI 场景返回完整客户端动作。
     */
    @Test
    void shouldCreateAllSupportedPaymentScenes() {
        PrepayResponse nativeResponse = new PrepayResponse();
        nativeResponse.setCodeUrl("weixin://native-code");
        com.wechat.pay.java.service.payments.h5.model.PrepayResponse h5Response =
                new com.wechat.pay.java.service.payments.h5.model.PrepayResponse();
        h5Response.setH5Url("https://wx.tenpay.com/h5");
        com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse appResponse =
                new com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse();
        appResponse.setAppid("wx-app"); appResponse.setPartnerId("merchant");
        appResponse.setPrepayId("prepay-app"); appResponse.setPackageVal("Sign=WXPay");
        appResponse.setNonceStr("nonce"); appResponse.setTimestamp("1"); appResponse.setSign("sign");
        com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse jsapiResponse =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse();
        jsapiResponse.setAppId("wx-app"); jsapiResponse.setTimeStamp("1");
        jsapiResponse.setNonceStr("nonce"); jsapiResponse.setPackageVal("prepay_id=jsapi");
        jsapiResponse.setSignType("RSA"); jsapiResponse.setPaySign("sign");
        when(sdk.nativePrepay(any())).thenReturn(nativeResponse);
        when(sdk.h5Prepay(any())).thenReturn(h5Response);
        when(sdk.appPrepay(any())).thenReturn(appResponse);
        when(sdk.jsapiPrepay(any())).thenReturn(jsapiResponse);

        assertThat(provider.create(request(PayScene.QR_CODE)).getAction().getType())
                .isEqualTo(PayActionType.QR_CODE_URL);
        assertThat(provider.create(request(PayScene.WAP)).getAction().getParameters())
                .containsEntry("redirectUrl", "https://wx.tenpay.com/h5");
        assertThat(provider.create(request(PayScene.APP)).getAction().getParameters())
                .containsEntry("prepayId", "prepay-app");
        assertThat(provider.create(request(PayScene.JSAPI)).getAction().getParameters())
                .containsEntry("package", "prepay_id=jsapi");

        ArgumentCaptor<PrepayRequest> captor = ArgumentCaptor.forClass(PrepayRequest.class);
        verify(sdk).nativePrepay(captor.capture());
        assertThat(captor.getValue().getAmount().getTotal()).isEqualTo(1);
        assertThatThrownBy(() -> provider.create(request(PayScene.PAGE)))
                .isInstanceOf(PayException.class)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.REQUEST_INVALID);
    }

    /**
     * 验证查询、关单、退款与退款查询状态映射。
     */
    @Test
    void shouldMapCommonOperations() {
        when(sdk.queryByOutTradeNo(any())).thenReturn(transaction(Transaction.TradeStateEnum.SUCCESS));
        Refund refund = new Refund();
        refund.setOutTradeNo("ORDER-1"); refund.setOutRefundNo("REFUND-1");
        refund.setRefundId("WX-REFUND-1"); refund.setStatus(Status.PROCESSING);
        when(sdk.createRefund(any())).thenReturn(refund);
        when(sdk.queryRefund(any())).thenAnswer(invocation -> {
            refund.setStatus(Status.SUCCESS);
            return refund;
        });

        assertThat(provider.query(PayQueryRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.SUCCESS);
        assertThat(provider.close(PayCloseRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.CLOSED);
        assertThat(provider.refund(refundRequest()).getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(provider.queryRefund(RefundQueryRequest.builder().outRefundNo("REFUND-1").build()).getStatus())
                .isEqualTo(RefundStatus.SUCCESS);
    }

    /**
     * 验证回调会携带原始签名材料交给官方 NotificationParser。
     */
    @Test
    void shouldParseVerifiedNotification() {
        when(sdk.parseNotification(any())).thenReturn(transaction(Transaction.TradeStateEnum.SUCCESS));
        PayNotificationRequest request = PayNotificationRequest.builder().provider("wechat")
                .rawBody("{encrypted-body}")
                .header("Wechatpay-Serial", "serial")
                .header("Wechatpay-Timestamp", "1")
                .header("Wechatpay-Nonce", "nonce")
                .header("Wechatpay-Signature", "signature")
                .build();

        assertThat(provider.parseNotification(request).getStatus()).isEqualTo(PayStatus.SUCCESS);
        ArgumentCaptor<RequestParam> captor = ArgumentCaptor.forClass(RequestParam.class);
        verify(sdk).parseNotification(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("{encrypted-body}");
        assertThat(captor.getValue().getSignature()).isEqualTo("signature");
    }

    /**
     * 验证缺少回调签名或官方解析失败时不会交付业务通知。
     */
    @Test
    void shouldRejectInvalidNotification() {
        PayNotificationRequest missingSignature = PayNotificationRequest.builder()
                .provider("wechat").rawBody("body").build();
        assertThatThrownBy(() -> provider.parseNotification(missingSignature))
                .isInstanceOf(PayException.class)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.NOTIFICATION_INVALID);

        when(sdk.parseNotification(any())).thenThrow(new IllegalArgumentException("invalid signature"));
        assertThatThrownBy(() -> provider.parseNotification(validNotification()))
                .isInstanceOf(PayException.class)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.SIGNATURE_INVALID);
    }

    /**
     * 验证 H5 场景信息不完整时在调用官方 SDK 前失败。
     */
    @Test
    void shouldValidateH5SceneInformation() {
        WechatPayProperties invalid = new WechatPayProperties();
        invalid.setAppId("wx-app");
        invalid.setMchId("merchant");
        invalid.setNotifyUrl("https://example.com/notify");
        WechatPayProvider invalidProvider = new WechatPayProvider(sdk, invalid);

        assertThatThrownBy(() -> invalidProvider.create(request(PayScene.WAP)))
                .isInstanceOf(PayException.class)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.CONFIGURATION_INVALID);
    }

    private PayRequest request(PayScene scene) {
        PayRequest.Builder builder = PayRequest.builder().provider("wechat").scene(scene)
                .outTradeNo("ORDER-" + scene).subject("测试订单")
                .amount(new BigDecimal("0.01")).clientIp("127.0.0.1");
        if (scene == PayScene.JSAPI) { builder.payerId("openid"); }
        return builder.build();
    }

    private RefundRequest refundRequest() {
        return RefundRequest.builder().provider("wechat").outTradeNo("ORDER-1")
                .outRefundNo("REFUND-1").amount(new BigDecimal("0.01"))
                .totalAmount(new BigDecimal("0.10")).build();
    }

    private Transaction transaction(Transaction.TradeStateEnum state) {
        Transaction transaction = new Transaction();
        transaction.setOutTradeNo("ORDER-1"); transaction.setTransactionId("WX-1");
        transaction.setTradeState(state); transaction.setSuccessTime("2026-08-05T12:00:00+08:00");
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(1); amount.setCurrency("CNY"); transaction.setAmount(amount);
        return transaction;
    }

    private PayNotificationRequest validNotification() {
        return PayNotificationRequest.builder().provider("wechat").rawBody("body")
                .header("Wechatpay-Serial", "serial").header("Wechatpay-Timestamp", "1")
                .header("Wechatpay-Nonce", "nonce").header("Wechatpay-Signature", "signature").build();
    }
}
