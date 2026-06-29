package com.github.leyland.letool.mq.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MQ 消息监听器注解 —— 声明式消息消费，标注在方法上即可自动订阅指定主题.
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class OrderConsumer {
 *
 *     @MqListener(topic = "order-topic", tag = "create")
 *     public void handleOrderCreate(Message message) {
 *         Order order = JsonUtil.parseObject(message.getBody(), Order.class);
 *         // 处理新订单
 *     }
 *
 *     @MqListener(topic = "order-topic", tag = "*")
 *     public void handleAllOrderMessages(Message message) {
 *         // 处理所有订单消息
 *     }
 * }
 * }</pre>
 *
 * <h3>工作原理</h3>
 * <ul>
 *   <li>Spring 容器启动时，扫描所有标注了 {@code @MqListener} 的方法</li>
 *   <li>根据注解属性自动调用 {@code MqTemplate.subscribe()} 注册监听器</li>
 *   <li>当前版本需配合后置处理器使用；后续版本将提供自动注册机制</li>
 * </ul>
 *
 * <h3>与编程式订阅的区别</h3>
 * <ul>
 *   <li>声明式（本注解）：适合固定消费逻辑，启动时自动注册，配置集中</li>
 *   <li>编程式（{@link com.github.leyland.letool.mq.core.MqTemplate#subscribe MqTemplate.subscribe}）：
 *       适合动态消费，运行时可随时注册/取消</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqListener {

    /**
     * 消息主题 —— 指定要订阅的目标主题（必填）.
     *
     * @return 主题名称，如 {@code "order-topic"}
     */
    String topic();

    /**
     * 消息标签 —— 用于消费端过滤，默认 {@code "*"} 表示匹配全部标签.
     *
     * <p>RocketMQ 原生支持按 Tag 过滤；RabbitMQ/Kafka 通过消息头部模拟.</p>
     *
     * @return 标签表达式，{@code "*"} 表示全部匹配
     */
    String tag() default "*";

    /**
     * 消费者组 —— 用于标识消费者组别，默认 {@code "default"}.
     *
     * <p>同一组内的消费者共享消费进度（负载均衡），不同组各自独立消费.</p>
     *
     * @return 消费者组名
     */
    String group() default "default";
}
