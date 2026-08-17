package io.github.leylaragg.letool.pay.core;

import io.github.leylaragg.letool.pay.exception.PayException;
import io.github.leylaragg.letool.pay.model.PayAction;
import io.github.leylaragg.letool.pay.model.PayActionType;
import io.github.leylaragg.letool.pay.model.PayCloseRequest;
import io.github.leylaragg.letool.pay.model.PayNotificationRequest;
import io.github.leylaragg.letool.pay.model.PayQueryRequest;
import io.github.leylaragg.letool.pay.model.PayRequest;
import io.github.leylaragg.letool.pay.model.PayResponse;
import io.github.leylaragg.letool.pay.model.PayScene;
import io.github.leylaragg.letool.pay.model.PayStatus;
import io.github.leylaragg.letool.pay.model.RefundQueryRequest;
import io.github.leylaragg.letool.pay.model.RefundRequest;
import io.github.leylaragg.letool.pay.model.RefundResponse;
import io.github.leylaragg.letool.pay.model.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付生产级公共契约测试。
 *
 * <p>该测试先描述不可变模型、金额精度和回调原文保护等目标行为，
 * 再由生产代码实现对应契约。</p>
 */
class PayProductionContractTest {

    /**
     * 验证支付请求会规范化 Provider，并保护扩展参数快照。
     */
    @Test
    void shouldBuildImmutablePayRequest() {
        PayRequest request = PayRequest.builder()
                .provider(" WeChat ")
                .scene(PayScene.QR_CODE)
                .outTradeNo("ORDER-001")
                .subject("测试订单")
                .amount(new BigDecimal("0.01"))
                .notifyUrl("https://example.com/pay/notify")
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .metadata("attach", "member-1")
                .build();

        assertThat(request.getProvider()).isEqualTo("wechat");
        assertThat(request.getCurrency()).isEqualTo("CNY");
        assertThat(request.getMetadata()).containsEntry("attach", "member-1");
        assertThatThrownBy(() -> request.getMetadata().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.toString()).doesNotContain("member-1");
    }

    /**
     * 验证支付金额必须为正数且不能产生分以下精度损失。
     */
    @Test
    void shouldRejectInvalidPaymentAmounts() {
        assertInvalidAmount("0");
        assertInvalidAmount("-0.01");
        assertInvalidAmount("0.001");

        assertThatThrownBy(() -> validPayRequest().currency("USD").build())
                .isInstanceOf(PayException.class);
    }

    /**
     * 验证查询和关闭请求至少包含一个平台可识别的订单标识。
     */
    @Test
    void shouldRequirePaymentIdentifier() {
        assertThatThrownBy(() -> PayQueryRequest.builder().provider("alipay").build())
                .isInstanceOf(PayException.class);
        assertThatThrownBy(() -> PayCloseRequest.builder().provider("alipay").build())
                .isInstanceOf(PayException.class);

        PayQueryRequest query = PayQueryRequest.builder()
                .provider("alipay")
                .transactionId("202608050001")
                .build();
        assertThat(query.getTransactionId()).isEqualTo("202608050001");
    }

    /**
     * 验证退款请求使用独立模型并保护扩展参数。
     */
    @Test
    void shouldBuildValidatedRefundRequest() {
        RefundRequest request = RefundRequest.builder()
                .provider("wechat")
                .outTradeNo("ORDER-001")
                .outRefundNo("REFUND-001")
                .amount(new BigDecimal("0.01"))
                .totalAmount(new BigDecimal("0.10"))
                .reason("用户退款")
                .metadata("operator", "system")
                .build();

        assertThat(request.getAmount()).isEqualByComparingTo("0.01");
        assertThat(request.getTotalAmount()).isEqualByComparingTo("0.10");
        assertThatThrownBy(() -> request.getMetadata().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> RefundRequest.builder()
                .provider("wechat")
                .outTradeNo("ORDER-001")
                .outRefundNo("REFUND-001")
                .amount(new BigDecimal("0.11"))
                .totalAmount(new BigDecimal("0.10"))
                .build()).isInstanceOf(PayException.class);

        RefundQueryRequest query = RefundQueryRequest.builder()
                .provider("wechat")
                .outRefundNo("REFUND-001")
                .build();
        assertThat(query.getOutRefundNo()).isEqualTo("REFUND-001");
    }

    /**
     * 验证回调请求保留原始正文，并以大小写不敏感方式读取 Header。
     */
    @Test
    void shouldProtectOriginalNotificationData() {
        PayNotificationRequest request = PayNotificationRequest.builder()
                .provider("wechat")
                .rawBody("{\"id\":\"NOTICE-1\"}")
                .header("Wechatpay-Signature", "signature")
                .formParameter("out_trade_no", "ORDER-001")
                .build();

        assertThat(request.getHeader("wechatpay-signature")).isEqualTo("signature");
        assertThat(request.getRawBody()).isEqualTo("{\"id\":\"NOTICE-1\"}");
        assertThatThrownBy(() -> request.getHeaders().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.getFormParameters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.toString()).doesNotContain("signature", "NOTICE-1");
    }

    /**
     * 验证支付和退款响应使用独立状态，并保护动作参数快照。
     */
    @Test
    void shouldBuildImmutableProviderResponses() {
        PayAction action = PayAction.of(PayActionType.QR_CODE_URL,
                Map.of("codeUrl", "weixin://wxpay/bizpayurl"));
        PayResponse payResponse = PayResponse.builder()
                .provider("wechat")
                .outTradeNo("ORDER-001")
                .status(PayStatus.PENDING)
                .action(action)
                .build();
        RefundResponse refundResponse = RefundResponse.builder()
                .provider("wechat")
                .outTradeNo("ORDER-001")
                .outRefundNo("REFUND-001")
                .status(RefundStatus.PROCESSING)
                .build();

        assertThat(payResponse.getAction().getType()).isEqualTo(PayActionType.QR_CODE_URL);
        assertThatThrownBy(() -> action.getParameters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(refundResponse.getStatus()).isEqualTo(RefundStatus.PROCESSING);
    }

    /**
     * 验证公共模型不会向调用方泄漏普通空指针异常。
     */
    @Test
    void shouldUseUnifiedExceptionForMissingRequiredObjects() {
        assertThatThrownBy(() -> PayAction.of(null, Map.of()))
                .isInstanceOf(PayException.class);
        assertThatThrownBy(() -> validPayRequest().scene(null).build())
                .isInstanceOf(PayException.class);
        assertThatThrownBy(() -> PayResponse.builder().provider("test").status(null).build())
                .isInstanceOf(PayException.class);
        assertThatThrownBy(() -> RefundResponse.builder().provider("test")
                .outRefundNo("REFUND-1").status(null).build())
                .isInstanceOf(PayException.class);
    }

    /**
     * 断言指定金额会被支付请求拒绝。
     *
     * @param amount 待验证金额
     */
    private void assertInvalidAmount(String amount) {
        assertThatThrownBy(() -> validPayRequest().amount(new BigDecimal(amount)).build())
                .isInstanceOf(PayException.class);
    }

    /**
     * 创建包含全部必填项的支付请求构建器。
     *
     * @return 支付请求构建器
     */
    private PayRequest.Builder validPayRequest() {
        return PayRequest.builder()
                .provider("alipay")
                .scene(PayScene.PAGE)
                .outTradeNo("ORDER-001")
                .subject("测试订单")
                .amount(new BigDecimal("0.01"));
    }
}
