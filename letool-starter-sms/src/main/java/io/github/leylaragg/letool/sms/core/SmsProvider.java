package io.github.leylaragg.letool.sms.core;

import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;

/**
 * 短信服务商统一扩展契约。
 *
 * <p>实现类只负责将公共请求转换为厂商请求并解析厂商响应，不负责业务重试和全局限流。</p>
 */
public interface SmsProvider {

    /**
     * 发送单个或批量短信请求。
     *
     * @param request 不可变短信请求
     * @return 结构化发送结果
     */
    SmsResult send(SmsRequest request);

    /**
     * 获取稳定且唯一的 Provider 名称。
     *
     * @return Provider 名称
     */
    String getProviderName();
}
