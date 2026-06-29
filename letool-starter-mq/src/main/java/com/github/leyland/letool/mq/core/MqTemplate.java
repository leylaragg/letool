package com.github.leyland.letool.mq.core;

import com.github.leyland.letool.mq.config.MqProperties;
import com.github.leyland.letool.mq.exception.MqException;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MQ 操作模板 —— 提供统一的消息发送、异步发送、延迟发送、订阅等 API，是业务代码操作 MQ 的唯一入口.
 *
 * <h3>核心能力</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>说明</th></tr>
 *   <tr><td>{@link #send(String, Object)}</td><td>同步发送消息（JSON 序列化）</td></tr>
 *   <tr><td>{@link #send(String, String, Object)}</td><td>同步发送带标签的消息</td></tr>
 *   <tr><td>{@link #sendAsync(String, Object)}</td><td>异步发送消息（返回 {@link CompletableFuture}）</td></tr>
 *   <tr><td>{@link #sendDelay(String, Object, long, TimeUnit)}</td><td>延迟发送消息</td></tr>
 *   <tr><td>{@link #subscribe(String, Consumer)}</td><td>订阅消息（Lambda 表达式）</td></tr>
 *   <tr><td>{@link #builder()}</td><td>流式 Builder 发送</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private MqTemplate mqTemplate;
 *
 * // 1. 简单发送
 * mqTemplate.send("order-topic", orderPayload);
 *
 * // 2. 带标签发送
 * mqTemplate.send("order-topic", "create", orderPayload);
 *
 * // 3. 异步发送
 * mqTemplate.sendAsync("order-topic", orderPayload)
 *     .thenAccept(v -> log.info("发送成功"));
 *
 * // 4. 延迟发送（5秒后投递）
 * mqTemplate.sendDelay("order-topic", orderPayload, 5, TimeUnit.SECONDS);
 *
 * // 5. 订阅消费
 * mqTemplate.subscribe("order-topic", msg -> {
 *     Order order = JsonUtil.parseObject(msg.getBody(), Order.class);
 *     processOrder(order);
 * });
 *
 * // 6. Builder 流式发送
 * mqTemplate.builder()
 *     .topic("order")
 *     .tag("create")
 *     .body(orderPayload)
 *     .header("traceId", "abc123")
 *     .send();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MqTemplate {

    private static final Logger log = LoggerFactory.getLogger(MqTemplate.class);

    // ======================== 依赖字段 ========================

    /** 底层 MQ 提供者 */
    private final MqProvider mqProvider;

    /** MQ 配置属性 */
    private final MqProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 通过构造注入 MqProvider 和 MqProperties.
     *
     * @param mqProvider 消息队列提供者
     * @param properties MQ 配置属性
     */
    public MqTemplate(MqProvider mqProvider, MqProperties properties) {
        this.mqProvider = mqProvider;
        this.properties = properties;
    }

    // ======================== 同步发送 ========================

    /**
     * 同步发送消息 —— 将 payload 序列化为 JSON 后发送到指定主题.
     *
     * @param topic   消息主题
     * @param payload 消息负载（任意 Java 对象，自动序列化为 JSON）
     */
    public void send(String topic, Object payload) {
        send(topic, null, payload);
    }

    /**
     * 同步发送带标签的消息 —— 消费端可通过标签过滤.
     *
     * @param topic   消息主题
     * @param tag     消息标签（为 {@code null} 时等同于不带标签）
     * @param payload 消息负载（任意 Java 对象，自动序列化为 JSON）
     */
    public void send(String topic, String tag, Object payload) {
        try {
            String body = JsonUtil.toJsonString(payload);
            Message message = Message.of(topic, tag, payload);
            log.debug("[letool-mq] 发送消息: topic={}, tag={}, messageId={}", topic, tag, message.getMessageId());
            mqProvider.send(topic, tag, message);
        } catch (Exception e) {
            throw new MqException("MQ 消息发送失败: topic=" + topic + ", tag=" + tag, e);
        }
    }

    // ======================== 异步发送 ========================

    /**
     * 异步发送消息 —— 在 ForkJoinPool 公共线程池中执行发送.
     *
     * @param topic   消息主题
     * @param payload 消息负载
     * @return 异步发送的 {@link CompletableFuture}，可用于链式回调或异常处理
     */
    public CompletableFuture<Void> sendAsync(String topic, Object payload) {
        return CompletableFuture.runAsync(() -> send(topic, payload));
    }

    /**
     * 异步发送带标签的消息.
     *
     * @param topic   消息主题
     * @param tag     消息标签
     * @param payload 消息负载
     * @return 异步发送的 {@link CompletableFuture}
     */
    public CompletableFuture<Void> sendAsync(String topic, String tag, Object payload) {
        return CompletableFuture.runAsync(() -> send(topic, tag, payload));
    }

    // ======================== 延迟发送 ========================

    /**
     * 延迟发送消息 —— 指定延迟时间后投递.
     *
     * <p>注意：延迟消息的具体实现取决于底层 MQ 提供者：</p>
     * <ul>
     *   <li>RabbitMQ —— 使用延迟队列插件或 TTL + DLX</li>
     *   <li>RocketMQ —— 使用 delayTimeLevel</li>
     *   <li>Kafka —— 通过定时任务模拟（或使用第三方延迟队列）</li>
     *   <li>InMemory —— 使用 {@link java.util.concurrent.ScheduledExecutorService}</li>
     * </ul>
     *
     * @param topic   消息主题
     * @param payload 消息负载
     * @param delay   延迟时长
     * @param unit    延迟时间单位
     */
    public void sendDelay(String topic, Object payload, long delay, TimeUnit unit) {
        try {
            Message message = Message.of(topic, payload);
            // 将延迟信息写入消息头部，由 Provider 实现层解析
            message.addHeader("X-DELAY-MILLIS", String.valueOf(unit.toMillis(delay)));
            log.debug("[letool-mq] 延迟发送消息: topic={}, delay={}ms, messageId={}",
                    topic, unit.toMillis(delay), message.getMessageId());
            mqProvider.send(topic, message);
        } catch (Exception e) {
            throw new MqException("MQ 延迟消息发送失败: topic=" + topic + ", delay=" + delay + " " + unit, e);
        }
    }

    // ======================== 消息订阅 ========================

    /**
     * 订阅指定主题的消息 —— 收到消息时回调 consumer.
     *
     * <p>推荐使用 Lambda 表达式，内部会自动适配 {@link MessageListener} 接口.</p>
     *
     * @param topic    消息主题
     * @param consumer 消息消费回调（Lambda 表达式或方法引用）
     */
    public void subscribe(String topic, Consumer<Message> consumer) {
        MessageListener listener = consumer::accept;
        log.info("[letool-mq] 订阅消息: topic={}", topic);
        mqProvider.subscribe(topic, listener);
    }

    /**
     * 取消订阅.
     *
     * @param topic    消息主题
     * @param consumer 需取消的消费者回调（需与订阅时传入的实例一致）
     */
    public void unsubscribe(String topic, Consumer<Message> consumer) {
        MessageListener listener = consumer::accept;
        log.info("[letool-mq] 取消订阅: topic={}", topic);
        mqProvider.unsubscribe(topic, listener);
    }

    // ======================== Builder 入口 ========================

    /**
     * 获取流式消息构建器 —— 提供 {@code .topic().tag().body().send()} 流式发送体验.
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * mqTemplate.builder()
     *     .topic("order")
     *     .tag("create")
     *     .body(orderPayload)
     *     .header("traceId", "abc123")
     *     .send();
     * }</pre>
     *
     * @return 流式消息发送构建器
     */
    public Sender builder() {
        return new Sender();
    }

    // ======================== Getter ========================

    /**
     * 获取 MQ 配置属性.
     *
     * @return MqProperties 实例
     */
    public MqProperties getProperties() {
        return properties;
    }

    // ======================== 内部类：流式消息发送器 ========================

    /**
     * 流式消息发送构建器 —— 链式设置消息属性并以 {@link #send()} 或 {@link #sendAsync()} 结束.
     *
     * <p>本质上是 {@link MessageBuilder} 的增强版，构建完成后自动调用 {@link MqTemplate#send} 或
     * {@link MqTemplate#sendAsync}，避免手动调用两段 API.</p>
     */
    public class Sender {

        private final MessageBuilder messageBuilder = new MessageBuilder();

        /**
         * 设置消息主题.
         *
         * @param topic 消息主题
         * @return 当前 Sender（支持链式调用）
         */
        public Sender topic(String topic) {
            messageBuilder.topic(topic);
            return this;
        }

        /**
         * 设置消息标签.
         *
         * @param tag 消息标签
         * @return 当前 Sender（支持链式调用）
         */
        public Sender tag(String tag) {
            messageBuilder.tag(tag);
            return this;
        }

        /**
         * 设置消息体（自动 JSON 序列化）.
         *
         * @param payload 消息负载
         * @return 当前 Sender（支持链式调用）
         */
        public Sender body(Object payload) {
            messageBuilder.body(payload);
            return this;
        }

        /**
         * 添加自定义消息头部.
         *
         * @param key   头部键
         * @param value 头部值
         * @return 当前 Sender（支持链式调用）
         */
        public Sender header(String key, String value) {
            messageBuilder.header(key, value);
            return this;
        }

        /**
         * 构建并同步发送消息 —— 流式链的终点.
         */
        public void send() {
            Message message = messageBuilder.build();
            String tag = "*".equals(message.getTag()) ? null : message.getTag();
            mqProvider.send(message.getTopic(), tag, message);
        }

        /**
         * 构建并异步发送消息 —— 流式链的终点.
         *
         * @return 异步发送的 {@link CompletableFuture}
         */
        public CompletableFuture<Void> sendAsync() {
            Message message = messageBuilder.build();
            String tag = "*".equals(message.getTag()) ? null : message.getTag();
            return CompletableFuture.runAsync(() ->
                    mqProvider.send(message.getTopic(), tag, message));
        }
    }
}
