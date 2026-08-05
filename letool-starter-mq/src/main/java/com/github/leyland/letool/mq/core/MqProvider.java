package com.github.leyland.letool.mq.core;

import com.github.leyland.letool.mq.model.MqSendRequest;
import com.github.leyland.letool.mq.model.MqSendResult;

/**
 * MQ 发送 Provider 扩展契约。
 *
 * <p>Provider 只负责把统一发送请求交给具体消息基础设施。消费、确认、重试、死信、事务和顺序等
 * 生命周期能力由 Spring Cloud Stream 及具体 Binder 管理，不在此接口中重复抽象。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface MqProvider {

    /**
     * 返回稳定且唯一的 Provider 名称。
     *
     * @return 非空白 Provider 名称
     */
    String name();

    /**
     * 执行一次消息发送。
     *
     * @param request 已校验的发送请求
     * @return 非空结构化发送结果
     */
    MqSendResult send(MqSendRequest<?> request);
}
