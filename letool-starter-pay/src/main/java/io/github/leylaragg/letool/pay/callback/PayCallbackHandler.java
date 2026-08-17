package io.github.leylaragg.letool.pay.callback;

import io.github.leylaragg.letool.pay.model.PayNotification;

/**
 * 业务方处理已验签标准通知的扩展接口。
 *
 * <p>该接口是为用户实现而保留的真实扩展点。Letool 不会自动创建实现，
 * 业务项目应在数据库事务中完成通知幂等、订单状态迁移和审计记录。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@FunctionalInterface
public interface PayCallbackHandler {

    /**
     * 处理已经由 Provider 验签并标准化的支付通知。
     *
     * @param notification 标准化支付通知
     */
    void handle(PayNotification notification);
}
