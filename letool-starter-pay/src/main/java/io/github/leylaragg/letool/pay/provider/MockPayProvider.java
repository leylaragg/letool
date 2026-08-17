package io.github.leylaragg.letool.pay.provider;

import io.github.leylaragg.letool.pay.core.PayProvider;
import io.github.leylaragg.letool.pay.model.PayCloseRequest;
import io.github.leylaragg.letool.pay.model.PayNotification;
import io.github.leylaragg.letool.pay.model.PayNotificationRequest;
import io.github.leylaragg.letool.pay.model.PayQueryRequest;
import io.github.leylaragg.letool.pay.model.PayRequest;
import io.github.leylaragg.letool.pay.model.PayResponse;
import io.github.leylaragg.letool.pay.model.PayStatus;
import io.github.leylaragg.letool.pay.model.RefundQueryRequest;
import io.github.leylaragg.letool.pay.model.RefundRequest;
import io.github.leylaragg.letool.pay.model.RefundResponse;
import io.github.leylaragg.letool.pay.model.RefundStatus;

/**
 * 仅用于开发和自动化测试的确定性 Mock 支付 Provider。
 *
 * <p>该实现不会访问任何真实支付平台，必须通过
 * {@code letool.pay.mock.enabled=true} 显式启用。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class MockPayProvider implements PayProvider {

    /** {@inheritDoc} */
    @Override
    public String getProviderName() { return "mock"; }

    /** {@inheritDoc} */
    @Override
    public PayResponse create(PayRequest request) {
        return payResponse(request.getOutTradeNo(), PayStatus.PENDING);
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse query(PayQueryRequest request) {
        return payResponse(request.getOutTradeNo(), PayStatus.SUCCESS);
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse close(PayCloseRequest request) {
        return payResponse(request.getOutTradeNo(), PayStatus.CLOSED);
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse refund(RefundRequest request) {
        return RefundResponse.builder()
                .provider(getProviderName())
                .outTradeNo(request.getOutTradeNo())
                .transactionId(request.getTransactionId())
                .outRefundNo(request.getOutRefundNo())
                .refundId("MOCK-" + request.getOutRefundNo())
                .status(RefundStatus.PROCESSING)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse queryRefund(RefundQueryRequest request) {
        return RefundResponse.builder()
                .provider(getProviderName())
                .outRefundNo(request.getOutRefundNo())
                .refundId("MOCK-" + request.getOutRefundNo())
                .status(RefundStatus.SUCCESS)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public PayNotification parseNotification(PayNotificationRequest request) {
        return PayNotification.builder()
                .provider(getProviderName())
                .eventType("PAYMENT.SUCCESS")
                .outTradeNo(request.getFormParameters().getOrDefault("out_trade_no", "MOCK-ORDER"))
                .status(PayStatus.SUCCESS)
                .build();
    }

    private PayResponse payResponse(String outTradeNo, PayStatus status) {
        return PayResponse.builder()
                .provider(getProviderName())
                .outTradeNo(outTradeNo)
                .transactionId(outTradeNo == null ? null : "MOCK-" + outTradeNo)
                .status(status)
                .build();
    }
}
