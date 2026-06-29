package com.github.leyland.letool.mq.core;

import com.github.leyland.letool.tool.util.JsonUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQ 消息模型 —— 封装消息的完整元数据（ID、主题、标签、体、头部、时间戳）.
 *
 * <h3>结构说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>messageId</td><td>String</td><td>全局唯一标识（UUID），用于链路追踪与去重</td></tr>
 *   <tr><td>topic</td><td>String</td><td>消息主题（目标队列 / exchange）</td></tr>
 *   <tr><td>tag</td><td>String</td><td>消息标签（用于消费端过滤，{@code *} 表示全部）</td></tr>
 *   <tr><td>body</td><td>String</td><td>消息体（JSON 字符串）</td></tr>
 *   <tr><td>headers</td><td>Map</td><td>自定义头部（透传键值对）</td></tr>
 *   <tr><td>timestamp</td><td>long</td><td>消息创建时间戳（毫秒）</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 静态工厂
 * Message msg = Message.of("order-topic", orderPayload);
 *
 * // 构建器
 * Message msg = Message.builder()
 *     .topic("order-topic")
 *     .tag("create")
 *     .body(orderPayload)
 *     .header("traceId", "abc123")
 *     .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    // ======================== 字段 ========================

    /** 消息全局唯一 ID */
    private String messageId;

    /** 消息主题 */
    private String topic;

    /** 消息标签，默认 {@code *} 表示匹配全部 */
    private String tag;

    /** 消息体（JSON 字符串） */
    private String body;

    /** 自定义消息头部 */
    private Map<String, String> headers;

    /** 消息创建时间戳（毫秒） */
    private long timestamp;

    // ======================== 构造方法 ========================

    /** 无参构造（供序列化框架使用）. */
    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 创建指定主题和体的消息.
     *
     * @param topic 主题
     * @param body  JSON 字符串消息体
     */
    public Message(String topic, String body) {
        this();
        this.messageId = UUID.randomUUID().toString();
        this.topic = topic;
        this.tag = "*";
        this.body = body;
        this.headers = new HashMap<>();
    }

    /**
     * 创建指定主题、标签、体的消息.
     *
     * @param topic 主题
     * @param tag   标签
     * @param body  JSON 字符串消息体
     */
    public Message(String topic, String tag, String body) {
        this(topic, body);
        this.tag = tag;
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 将任意对象序列化为 JSON 并创建消息.
     *
     * @param topic   消息主题
     * @param payload 消息负载（任意 Java 对象，自动序列化为 JSON）
     * @return 新创建的 Message 实例
     */
    public static Message of(String topic, Object payload) {
        String body = JsonUtil.toJsonString(payload);
        return new Message(topic, body);
    }

    /**
     * 将任意对象序列化为 JSON 并创建带标签的消息.
     *
     * @param topic   消息主题
     * @param tag     消息标签
     * @param payload 消息负载（任意 Java 对象，自动序列化为 JSON）
     * @return 新创建的 Message 实例
     */
    public static Message of(String topic, String tag, Object payload) {
        String body = JsonUtil.toJsonString(payload);
        return new Message(topic, tag, body);
    }

    // ======================== 构建器入口 ========================

    /**
     * 获取 {@link MessageBuilder} 构建器 —— 推荐使用此方式创建复杂消息.
     *
     * @return MessageBuilder 实例
     */
    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    // ======================== 便捷方法 ========================

    /**
     * 添加自定义头部键值对.
     *
     * @param key   头部键
     * @param value 头部值
     * @return 当前 Message 实例（支持链式调用）
     */
    public Message addHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, value);
        return this;
    }

    /**
     * 获取指定头部的值.
     *
     * @param key 头部键
     * @return 头部值，不存在返回 {@code null}
     */
    public String getHeader(String key) {
        return this.headers != null ? this.headers.get(key) : null;
    }

    // ======================== Getters & Setters ========================

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // ======================== toString ========================

    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", topic='" + topic + '\'' +
                ", tag='" + tag + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
