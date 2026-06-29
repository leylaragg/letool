package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.tool.util.IdUtil;
import com.github.leyland.letool.tool.util.JsonUtil;

/**
 * WebSocket 消息模型，封装了消息 ID、类型、负载、时间戳和发送者信息。
 *
 * <p>该模型是 letool WebSocket 模块中消息传递的标准载体：
 * <ul>
 *   <li>{@code messageId} — 全局唯一的消息标识（UUID）</li>
 *   <li>{@code type} — 消息类型标识，用于消息路由分发</li>
 *   <li>{@code payload} — 消息体，JSON 字符串格式</li>
 *   <li>{@code timestamp} — 消息创建时间戳（毫秒）</li>
 *   <li>{@code senderId} — 发送者用户 ID</li>
 * </ul>
 *
 * <p>推荐使用静态工厂方法或 Builder 模式创建实例：</p>
 * <pre>{@code
 * // 工厂方法
 * WsMessage msg = WsMessage.of("chat", chatPayload);
 *
 * // Builder
 * WsMessage msg = WsMessage.builder()
 *     .type("notification")
 *     .payload(jsonPayload)
 *     .senderId("user001")
 *     .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsMessage {

    // ======================== 消息类型常量 ========================

    /** 文本消息类型标识 */
    public static final String TYPE_TEXT = "text";

    /** 心跳消息 - 客户端发送 */
    public static final String TYPE_PING = "ping";

    /** 心跳消息 - 服务端响应 */
    public static final String TYPE_PONG = "pong";

    /** 系统通知消息类型 */
    public static final String TYPE_NOTIFICATION = "notification";

    /** 错误消息类型 */
    public static final String TYPE_ERROR = "error";

    // ======================== 字段 ========================

    /** 消息唯一标识（UUID） */
    private String messageId;

    /** 消息类型，用于路由分发 */
    private String type;

    /** 消息负载，JSON 字符串格式 */
    private String payload;

    /** 消息创建时间戳（毫秒） */
    private long timestamp;

    /** 发送者用户 ID（服务端推送时为空） */
    private String senderId;

    // ======================== 构造 ========================

    public WsMessage() {
        this.messageId = IdUtil.simpleUUID();
        this.timestamp = System.currentTimeMillis();
    }

    public WsMessage(String type, String payload) {
        this();
        this.type = type;
        this.payload = payload;
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建指定类型和负载的消息。
     *
     * <p>负载对象会自动序列化为 JSON 字符串。</p>
     *
     * @param type    消息类型标识
     * @param payload 消息负载对象
     * @return 包含序列化负载的 WsMessage 实例
     */
    public static WsMessage of(String type, Object payload) {
        WsMessage message = new WsMessage();
        message.type = type;
        message.payload = JsonUtil.toJsonString(payload);
        return message;
    }

    /**
     * 创建纯文本消息，类型自动设为 {@code "text"}。
     *
     * @param content 文本内容
     * @return 类型为 "text" 的 WsMessage 实例
     */
    public static WsMessage text(String content) {
        WsMessage message = new WsMessage();
        message.type = TYPE_TEXT;
        message.payload = content;
        return message;
    }

    /**
     * 创建心跳响应消息（pong）。
     *
     * <p>服务端收到客户端 ping 后应回复 pong 以保持心跳。</p>
     *
     * @return 类型为 "pong" 的 WsMessage 实例
     */
    public static WsMessage pong() {
        WsMessage message = new WsMessage();
        message.type = TYPE_PONG;
        message.payload = String.valueOf(System.currentTimeMillis());
        return message;
    }

    /**
     * 创建错误消息。
     *
     * @param errorMessage 错误描述
     * @return 类型为 "error" 的 WsMessage 实例
     */
    public static WsMessage error(String errorMessage) {
        WsMessage message = new WsMessage();
        message.type = TYPE_ERROR;
        message.payload = errorMessage;
        return message;
    }

    // ======================== Builder ========================

    /**
     * 获取 Builder 实例。
     *
     * @return 新的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * WsMessage Builder 模式构建器。
     */
    public static class Builder {
        private String type;
        private String payload;
        private String senderId;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = JsonUtil.toJsonString(payload);
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        /**
         * 构建 WsMessage 实例。
         *
         * @return 构建完成的 WsMessage
         */
        public WsMessage build() {
            WsMessage message = new WsMessage();
            message.type = this.type;
            message.payload = this.payload;
            message.senderId = this.senderId;
            return message;
        }
    }

    // ======================== Getter / Setter ========================

    public String getMessageId() { return messageId; }

    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getPayload() { return payload; }

    public void setPayload(String payload) { this.payload = payload; }

    public long getTimestamp() { return timestamp; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSenderId() { return senderId; }

    public void setSenderId(String senderId) { this.senderId = senderId; }

    // ======================== 工具方法 ========================

    /**
     * 将负载解析为指定类型的对象。
     *
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 反序列化后的对象
     */
    public <T> T payloadAs(Class<T> clazz) {
        return JsonUtil.parseObject(payload, clazz);
    }

    @Override
    public String toString() {
        return "WsMessage{messageId='" + messageId + "', type='" + type + "', senderId='" + senderId + "', timestamp=" + timestamp + "}";
    }
}
