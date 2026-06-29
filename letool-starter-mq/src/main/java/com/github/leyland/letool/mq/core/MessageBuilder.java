package com.github.leyland.letool.mq.core;

import com.github.leyland.letool.tool.util.JsonUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息构建器 —— 提供流式 API 构建 {@link Message} 对象.
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Message msg = new MessageBuilder()
 *     .topic("order-topic")
 *     .tag("create")
 *     .body(orderObject)
 *     .header("traceId", "abc123")
 *     .header("userId", "10086")
 *     .build();
 * }</pre>
 *
 * <p>注意：通常不需要直接使用此类，推荐通过 {@link Message#builder()} 获取实例，
 * 或通过 {@link MqTemplate} 的 Builder 模式直接发送.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MessageBuilder {

    private String topic;
    private String tag = "*";
    private String body;
    private final Map<String, String> headers = new HashMap<>();

    /**
     * 设置消息主题（必填）.
     *
     * @param topic 消息主题
     * @return 当前构建器（支持链式调用）
     */
    public MessageBuilder topic(String topic) {
        this.topic = topic;
        return this;
    }

    /**
     * 设置消息标签（选填，默认 {@code *} 匹配全部）.
     *
     * @param tag 消息标签
     * @return 当前构建器（支持链式调用）
     */
    public MessageBuilder tag(String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * 设置消息体 —— 自动将 Java 对象序列化为 JSON 字符串.
     *
     * @param payload 消息负载（任意 Java 对象）
     * @return 当前构建器（支持链式调用）
     */
    public MessageBuilder body(Object payload) {
        this.body = JsonUtil.toJsonString(payload);
        return this;
    }

    /**
     * 直接设置 JSON 字符串消息体（不做序列化处理）.
     *
     * @param jsonBody JSON 格式的消息体字符串
     * @return 当前构建器（支持链式调用）
     */
    public MessageBuilder bodyJson(String jsonBody) {
        this.body = jsonBody;
        return this;
    }

    /**
     * 添加自定义消息头部键值对.
     *
     * @param key   头部键
     * @param value 头部值
     * @return 当前构建器（支持链式调用）
     */
    public MessageBuilder header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    /**
     * 构建 {@link Message} 实例.
     *
     * @return 构建完成的 Message 对象（topic 和 body 为必填）
     * @throws IllegalArgumentException 如果 topic 或 body 为空
     */
    public Message build() {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("消息 topic 不能为空");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("消息 body 不能为空");
        }

        Message message = new Message();
        message.setTopic(this.topic);
        message.setTag(this.tag);
        message.setBody(this.body);
        message.setHeaders(new HashMap<>(this.headers));
        return message;
    }
}
