package com.github.leyland.letool.pay.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayActionType;
import com.github.leyland.letool.pay.model.PayCloseRequest;
import com.github.leyland.letool.pay.model.PayNotificationRequest;
import com.github.leyland.letool.pay.model.PayQueryRequest;
import com.github.leyland.letool.pay.model.PayRequest;
import com.github.leyland.letool.pay.model.PayResponse;
import com.github.leyland.letool.pay.model.PayScene;
import com.github.leyland.letool.pay.model.PayStatus;
import com.github.leyland.letool.pay.model.RefundQueryRequest;
import com.github.leyland.letool.pay.model.RefundRequest;
import com.github.leyland.letool.pay.model.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AlipayPayProvider} 官方 SDK 适配契约测试。
 */
class AlipayPayProviderTest {

    private AlipayClient client;
    private AlipayPayProperties properties;

    /**
     * 初始化测试依赖。
     */
    @BeforeEach
    void setUp() {
        client = mock(AlipayClient.class);
        properties = new AlipayPayProperties();
        properties.setAlipayPublicKey("public-key");
    }

    /**
     * 验证四种下单场景分别返回调用端需要的动作类型。
     */
    @Test
    void shouldCreateAllSupportedPaymentScenes() throws Exception {
        AlipayTradePagePayResponse page = success(new AlipayTradePagePayResponse());
        page.setBody("<form>page</form>");
        AlipayTradeWapPayResponse wap = success(new AlipayTradeWapPayResponse());
        wap.setBody("<form>wap</form>");
        AlipayTradeAppPayResponse app = success(new AlipayTradeAppPayResponse());
        app.setBody("signed-app-order");
        AlipayTradePrecreateResponse qr = success(new AlipayTradePrecreateResponse());
        qr.setOutTradeNo("ORDER-QR_CODE");
        qr.setQrCode("https://qr.example/1");

        when(client.pageExecute(any())).thenAnswer(invocation -> {
            AlipayRequest<?> request = invocation.getArgument(0);
            return request instanceof AlipayTradePagePayRequest ? page : wap;
        });
        when(client.sdkExecute(any(AlipayTradeAppPayRequest.class))).thenReturn(app);
        when(client.execute(any(AlipayTradePrecreateRequest.class))).thenReturn(qr);
        AlipayPayProvider provider = provider((params, ignoredProperties) -> true);

        assertThat(provider.create(request(PayScene.PAGE)).getAction().getType())
                .isEqualTo(PayActionType.FORM_HTML);
        assertThat(provider.create(request(PayScene.WAP)).getAction().getParameters())
                .containsEntry("formHtml", "<form>wap</form>");
        assertThat(provider.create(request(PayScene.APP)).getAction().getType())
                .isEqualTo(PayActionType.APP_ORDER_STRING);
        assertThat(provider.create(request(PayScene.QR_CODE)).getAction().getParameters())
                .containsEntry("codeUrl", "https://qr.example/1");
    }

    /**
     * 验证查询、关单、退款和退款查询状态会被标准化。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldMapCommonOperationStatuses() throws Exception {
        when(client.execute(any(AlipayRequest.class)))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        AlipayPayProvider provider = provider((params, ignoredProperties) -> true);

        assertThat(provider.query(PayQueryRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.SUCCESS);
        assertThat(provider.close(PayCloseRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.CLOSED);
        assertThat(provider.refund(refundRequest()).getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(provider.queryRefund(RefundQueryRequest.builder().outRefundNo("REFUND-1").build()).getStatus())
                .isEqualTo(RefundStatus.PROCESSING);
    }

    /**
     * 验证回调必须先通过官方算法验签，然后才会转换标准通知。
     */
    @Test
    void shouldRequireSignatureBeforeParsingNotification() {
        Map<String, String> form = new HashMap<>();
        form.put("sign", "signature");
        form.put("sign_type", "RSA2");
        form.put("trade_status", "TRADE_SUCCESS");
        form.put("out_trade_no", "ORDER-1");
        form.put("trade_no", "ALI-1");
        form.put("total_amount", "0.01");
        PayNotificationRequest request = notification(form);

        assertThat(provider((params, ignoredProperties) -> true).parseNotification(request).getStatus())
                .isEqualTo(PayStatus.SUCCESS);
        assertThatThrownBy(() -> provider((params, ignoredProperties) -> false).parseNotification(request))
                .isInstanceOf(PayException.class)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.SIGNATURE_INVALID);
    }

    /**
     * 验证 SDK 异常会保留原因链并标记为结果未知。
     */
    @Test
    void shouldPreserveSdkFailureCause() throws Exception {
        AlipayApiException cause = new AlipayApiException("network error");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenThrow(cause);

        assertThatThrownBy(() -> provider((params, ignoredProperties) -> true)
                .query(PayQueryRequest.builder().outTradeNo("ORDER-1").build()))
                .isInstanceOf(PayException.class)
                .hasCause(cause)
                .extracting(error -> ((PayException) error).getErrorCode())
                .isEqualTo(PayErrorCode.OPERATION_FAILED);
    }

    private AlipayPayProvider provider(AlipayNotificationVerifier verifier) {
        return new AlipayPayProvider(client, properties, verifier);
    }

    private PayRequest request(PayScene scene) {
        return PayRequest.builder().provider("alipay").scene(scene)
                .outTradeNo("ORDER-" + scene).subject("测试订单")
                .amount(new BigDecimal("0.01")).returnUrl("https://example.com/return").build();
    }

    private RefundRequest refundRequest() {
        return RefundRequest.builder().provider("alipay").outTradeNo("ORDER-1")
                .outRefundNo("REFUND-1").amount(new BigDecimal("0.01")).build();
    }

    private PayNotificationRequest notification(Map<String, String> form) {
        PayNotificationRequest.Builder builder = PayNotificationRequest.builder().provider("alipay");
        form.forEach(builder::formParameter);
        return builder.build();
    }

    private AlipayResponse responseFor(AlipayRequest<?> request) {
        if (request instanceof AlipayTradeQueryRequest) {
            AlipayTradeQueryResponse response = success(new AlipayTradeQueryResponse());
            response.setOutTradeNo("ORDER-1");
            response.setTradeNo("ALI-1");
            response.setTradeStatus("TRADE_SUCCESS");
            return response;
        }
        if (request instanceof AlipayTradeCloseRequest) {
            AlipayTradeCloseResponse response = success(new AlipayTradeCloseResponse());
            response.setOutTradeNo("ORDER-1");
            return response;
        }
        if (request instanceof AlipayTradeRefundRequest) {
            AlipayTradeRefundResponse response = success(new AlipayTradeRefundResponse());
            response.setOutTradeNo("ORDER-1");
            response.setTradeNo("ALI-1");
            return response;
        }
        AlipayTradeFastpayRefundQueryResponse response = success(new AlipayTradeFastpayRefundQueryResponse());
        response.setOutRequestNo("REFUND-1");
        response.setRefundStatus("REFUND_PROCESSING");
        return response;
    }

    private <T extends AlipayResponse> T success(T response) {
        response.setCode("10000");
        response.setMsg("Success");
        return response;
    }
}
