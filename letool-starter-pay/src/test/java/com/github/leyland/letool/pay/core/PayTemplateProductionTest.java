package com.github.leyland.letool.pay.core;

import com.github.leyland.letool.pay.config.PayProperties;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayCloseRequest;
import com.github.leyland.letool.pay.model.PayNotification;
import com.github.leyland.letool.pay.model.PayNotificationRequest;
import com.github.leyland.letool.pay.model.PayQueryRequest;
import com.github.leyland.letool.pay.model.PayRequest;
import com.github.leyland.letool.pay.model.PayResponse;
import com.github.leyland.letool.pay.model.PayScene;
import com.github.leyland.letool.pay.model.PayStatus;
import com.github.leyland.letool.pay.model.RefundQueryRequest;
import com.github.leyland.letool.pay.model.RefundRequest;
import com.github.leyland.letool.pay.model.RefundResponse;
import com.github.leyland.letool.pay.model.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PayTemplate} 生产路由契约测试。
 */
class PayTemplateProductionTest {

    /**
     * 验证请求可以显式指定 Provider，也可以使用默认 Provider。
     */
    @Test
    void shouldRouteExplicitAndDefaultProvider() {
        PayProperties properties = new PayProperties();
        properties.setDefaultProvider(" ALIPAY ");
        TestPayProvider alipay = new TestPayProvider("alipay");
        TestPayProvider wechat = new TestPayProvider("wechat");
        PayTemplate template = new PayTemplate(List.of(alipay, wechat), properties);

        PayResponse explicit = template.create(validPayRequest(" WeChat "));
        PayResponse defaultResponse = template.create(validPayRequest(null));

        assertThat(explicit.getProvider()).isEqualTo("wechat");
        assertThat(defaultResponse.getProvider()).isEqualTo("alipay");
        assertThat(template.getProviderNames()).containsExactlyInAnyOrder("alipay", "wechat");
    }

    /**
     * 验证所有公共操作都会路由到同一个标准 Provider 契约。
     */
    @Test
    void shouldDelegateAllOperations() {
        PayProperties properties = new PayProperties();
        properties.setDefaultProvider("test");
        TestPayProvider provider = new TestPayProvider("test");
        PayTemplate template = new PayTemplate(List.of(provider), properties);

        assertThat(template.query(PayQueryRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.SUCCESS);
        assertThat(template.close(PayCloseRequest.builder().outTradeNo("ORDER-1").build()).getStatus())
                .isEqualTo(PayStatus.CLOSED);
        assertThat(template.refund(validRefundRequest()).getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(template.queryRefund(RefundQueryRequest.builder().outRefundNo("REFUND-1").build()).getStatus())
                .isEqualTo(RefundStatus.SUCCESS);
        assertThat(template.parseNotification(PayNotificationRequest.builder()
                .provider("test")
                .rawBody("signed-body")
                .build()).getStatus()).isEqualTo(PayStatus.SUCCESS);
    }

    /**
     * 验证重复 Provider 名称会在启动阶段快速失败。
     */
    @Test
    void shouldRejectDuplicateProviderNames() {
        PayProperties properties = new PayProperties();
        assertThatThrownBy(() -> new PayTemplate(
                List.of(new TestPayProvider(" Test "), new TestPayProvider("test")), properties))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("重复");
    }

    /**
     * 验证注册多个 Provider 时必须明确配置默认 Provider。
     */
    @Test
    void shouldRequireDefaultForMultipleProviders() {
        assertThatThrownBy(() -> new PayTemplate(
                List.of(new TestPayProvider("alipay"), new TestPayProvider("wechat")),
                new PayProperties()))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("default-provider");

        PayProperties properties = new PayProperties();
        properties.setDefaultProvider("missing");
        assertThatThrownBy(() -> new PayTemplate(List.of(new TestPayProvider("alipay")), properties))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("missing");
    }

    /**
     * 验证默认 Provider 缺失或请求指定未知 Provider 时会明确失败。
     */
    @Test
    void shouldRejectMissingProviderRoute() {
        PayTemplate template = new PayTemplate(List.of(new TestPayProvider("test")), new PayProperties());

        assertThatThrownBy(() -> template.create(validPayRequest(null)))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("default-provider");
        assertThatThrownBy(() -> template.create(validPayRequest("unknown")))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> template.create(null))
                .isInstanceOf(PayException.class)
                .hasMessageContaining("支付请求");
    }

    /**
     * 创建测试支付请求。
     *
     * @param provider 支付 Provider；可为 {@code null}
     * @return 支付请求
     */
    private PayRequest validPayRequest(String provider) {
        return PayRequest.builder()
                .provider(provider)
                .scene(PayScene.QR_CODE)
                .outTradeNo("ORDER-1")
                .subject("测试订单")
                .amount(new BigDecimal("0.01"))
                .build();
    }

    /**
     * 创建测试退款请求。
     *
     * @return 退款请求
     */
    private RefundRequest validRefundRequest() {
        return RefundRequest.builder()
                .outTradeNo("ORDER-1")
                .outRefundNo("REFUND-1")
                .amount(new BigDecimal("0.01"))
                .build();
    }

    /**
     * 记录调用结果的测试支付 Provider。
     */
    private static final class TestPayProvider implements PayProvider {

        private final String name;

        private TestPayProvider(String name) {
            this.name = name;
        }

        /** @return Provider 名称 */
        @Override
        public String getProviderName() { return name; }

        /** @param request 支付请求
         * @return 等待支付响应 */
        @Override
        public PayResponse create(PayRequest request) {
            return PayResponse.builder().provider(name.trim()).outTradeNo(request.getOutTradeNo())
                    .status(PayStatus.PENDING).build();
        }

        /** @param request 查询请求
         * @return 支付成功响应 */
        @Override
        public PayResponse query(PayQueryRequest request) {
            return PayResponse.builder().provider(name.trim()).outTradeNo(request.getOutTradeNo())
                    .status(PayStatus.SUCCESS).build();
        }

        /** @param request 关闭请求
         * @return 已关闭响应 */
        @Override
        public PayResponse close(PayCloseRequest request) {
            return PayResponse.builder().provider(name.trim()).outTradeNo(request.getOutTradeNo())
                    .status(PayStatus.CLOSED).build();
        }

        /** @param request 退款请求
         * @return 退款处理中响应 */
        @Override
        public RefundResponse refund(RefundRequest request) {
            return RefundResponse.builder().provider(name.trim()).outTradeNo(request.getOutTradeNo())
                    .outRefundNo(request.getOutRefundNo()).status(RefundStatus.PROCESSING).build();
        }

        /** @param request 退款查询请求
         * @return 退款成功响应 */
        @Override
        public RefundResponse queryRefund(RefundQueryRequest request) {
            return RefundResponse.builder().provider(name.trim()).outRefundNo(request.getOutRefundNo())
                    .status(RefundStatus.SUCCESS).build();
        }

        /** @param request 原始回调请求
         * @return 标准化支付通知 */
        @Override
        public PayNotification parseNotification(PayNotificationRequest request) {
            return PayNotification.builder().provider(name.trim()).eventType("PAYMENT.SUCCESS")
                    .outTradeNo("ORDER-1").status(PayStatus.SUCCESS).build();
        }
    }
}
