package com.github.leyland.letool.mq.core;

/**
 * MQ 消息监听器 —— 函数式接口，用于定义消息到达时的回调逻辑.
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Lambda 表达式
 * mqTemplate.subscribe("order-topic", message -> {
 *     Order order = JsonUtil.parseObject(message.getBody(), Order.class);
 *     System.out.println("收到订单: " + order.getId());
 * });
 *
 * // 方法引用
 * mqTemplate.subscribe("order-topic", this::handleOrder);
 * }</pre>
 *
 * <p>注意：本接口与 {@link com.github.leyland.letool.mq.annotation.MqListener @MqListener}
 * 注解配合使用：注解用于声明式定义（启动时自动注册），函数式接口用于编程式订阅（运行时动态注册）.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@FunctionalInterface
public interface MessageListener {

    /**
     * 消息到达时的回调方法 —— 在 MQ 消费者线程中同步调用.
     *
     * <p>实现时注意：</p>
     * <ul>
     *   <li>避免在方法内执行耗时操作，否则会阻塞消费线程</li>
     *   <li>如需异步处理，请在方法内将消息提交到线程池</li>
     *   <li>抛出未捕获异常将触发 MQ 的重试机制（取决于具体实现）</li>
     * </ul>
     *
     * @param message 接收到的消息对象，包含完整的元数据与消息体
     */
    void onMessage(Message message);
}
