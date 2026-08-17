package io.github.leylaragg.letool.sms.core;

import io.github.leylaragg.letool.sms.model.SmsRequest;

/**
 * 短信发送尝试限流扩展点。
 *
 * <p>多节点应用可以注册分布式实现替换默认本地实现。</p>
 */
@FunctionalInterface
public interface SmsRateLimiter {

    /**
     * 检查并记录一次短信发送尝试。
     *
     * @param request 短信请求
     */
    void check(SmsRequest request);

    /**
     * 创建不执行限流的实现。
     *
     * @return 无操作限流器
     */
    static SmsRateLimiter noOp() {
        return request -> {
            // 显式空实现用于关闭框架内限流。
        };
    }
}
