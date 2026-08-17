package io.github.leylaragg.letool.pay.core;

import io.github.leylaragg.letool.pay.model.PayCloseRequest;
import io.github.leylaragg.letool.pay.model.PayNotification;
import io.github.leylaragg.letool.pay.model.PayNotificationRequest;
import io.github.leylaragg.letool.pay.model.PayQueryRequest;
import io.github.leylaragg.letool.pay.model.PayRequest;
import io.github.leylaragg.letool.pay.model.PayResponse;
import io.github.leylaragg.letool.pay.model.RefundQueryRequest;
import io.github.leylaragg.letool.pay.model.RefundRequest;
import io.github.leylaragg.letool.pay.model.RefundResponse;

/**
 * 支付平台适配器的统一生产契约。
 *
 * <p>实现类负责调用官方 SDK、映射平台状态以及强制完成回调验签或解密；
 * 业务方仅通过 {@link PayTemplate} 使用标准模型。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface PayProvider {

    /**
     * 获取稳定且唯一的 Provider 名称。
     *
     * @return Provider 名称
     */
    String getProviderName();

    /**
     * 创建支付订单。
     *
     * @param request 支付请求
     * @return 标准化支付响应
     */
    PayResponse create(PayRequest request);

    /**
     * 查询支付订单。
     *
     * @param request 查询请求
     * @return 标准化支付响应
     */
    PayResponse query(PayQueryRequest request);

    /**
     * 关闭支付订单。
     *
     * @param request 关闭请求
     * @return 标准化支付响应
     */
    PayResponse close(PayCloseRequest request);

    /**
     * 发起退款。
     *
     * @param request 退款请求
     * @return 标准化退款响应
     */
    RefundResponse refund(RefundRequest request);

    /**
     * 查询退款。
     *
     * @param request 退款查询请求
     * @return 标准化退款响应
     */
    RefundResponse queryRefund(RefundQueryRequest request);

    /**
     * 验签、解密并解析支付平台通知。
     *
     * <p>实现类不得提供跳过验签的开关；验证失败必须抛出支付异常。</p>
     *
     * @param request 原始回调请求
     * @return 标准化支付通知
     */
    PayNotification parseNotification(PayNotificationRequest request);
}
