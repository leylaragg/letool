package com.github.leyland.letool.mq.core;

/**
 * MQ 消息提供者接口 —— 定义统一的消息队列操作规范.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>统一 RabbitMQ / RocketMQ / Kafka 的消息发送与订阅 API</li>
 *   <li>业务代码只需依赖此接口，无需关心底层 MQ 实现</li>
 *   <li>切换 MQ 中间件时无需修改业务代码</li>
 * </ul>
 *
 * <h3>实现指南</h3>
 * <p>实现类需要注入对应的 MQ 客户端（如 RabbitTemplate、RocketMQTemplate、KafkaTemplate），
 * 并将 SDK 的原生 API 适配为本接口的方法.</p>
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@link com.github.leyland.letool.mq.provider.InMemoryMqProvider InMemoryMqProvider}
 *       —— 内存队列实现，无需外部依赖</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface MqProvider {

    // ======================== 消息发送 ========================

    /**
     * 发送消息到指定主题.
     *
     * @param topic   消息主题（对应 RabbitMQ exchange/routingKey、RocketMQ topic、Kafka topic）
     * @param message 消息对象，包含消息体、标签、头部等元数据
     */
    void send(String topic, Message message);

    /**
     * 发送带标签的消息到指定主题 —— 标签用于消息过滤，消费者可按标签选择性消费.
     *
     * <p>对应 RocketMQ 的 Tag 过滤机制；RabbitMQ 和 Kafka 可通过 header 模拟.</p>
     *
     * @param topic   消息主题
     * @param tag     消息标签（为 {@code null} 或 {@code "*"} 表示不区分标签）
     * @param message 消息对象
     */
    void send(String topic, String tag, Message message);

    // ======================== 消息订阅 ========================

    /**
     * 订阅指定主题 —— 收到新消息时回调 {@link MessageListener#onMessage(Message)}.
     *
     * @param topic    消息主题
     * @param listener 消息监听器
     */
    void subscribe(String topic, MessageListener listener);

    /**
     * 取消订阅指定主题的指定监听器.
     *
     * @param topic    消息主题
     * @param listener 消息监听器（需与 {@link #subscribe(String, MessageListener)} 传入的实例一致）
     */
    void unsubscribe(String topic, MessageListener listener);
}
