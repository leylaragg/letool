package com.github.leyland.letool.net.protocol;

import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.tool.util.JsonUtil;

/**
 * JSON 协议编解码器 —— 使用 Fastjson2 将 Java 对象序列化为 JSON 字节流.
 *
 * <p>适用于需要结构化、可读性强的 TCP 通信场景（类似 REST over TCP）.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JsonProtocolCodec<UserRequest> codec = new JsonProtocolCodec<>(UserRequest.class);
 * byte[] bytes = codec.encode(new UserRequest("hello"));
 * UserRequest req = codec.decode(bytes);
 * }</pre>
 *
 * @param <T> 消息对象的 Java 类型
 * @author leyland
 * @since 2.0.0
 */
public class JsonProtocolCodec<T> implements ProtocolCodec {

    // ======================== 字段 ========================

    /** 消息对象的 Java 类型 */
    private final Class<T> messageType;

    // ======================== 构造器 ========================

    /**
     * 构造 JSON 协议编解码器.
     *
     * @param messageType 消息对象的 Java 类型，用于反序列化时确定目标类型
     * @throws IllegalArgumentException 如果 messageType 为 {@code null}
     */
    public JsonProtocolCodec(Class<T> messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType must not be null");
        }
        this.messageType = messageType;
    }

    // ======================== encode ========================

    /**
     * 将消息对象编码为 JSON 字节数组.
     *
     * @param message 待编码的消息对象（需与 messageType 兼容）
     * @return JSON 字节数组
     * @throws NetException 如果消息为 {@code null}
     */
    @Override
    @SuppressWarnings("unchecked")
    public byte[] encode(Object message) {
        if (message == null) {
            throw new NetException("Cannot encode null message with JsonProtocolCodec");
        }
        return JsonUtil.toJsonBytes(message);
    }

    // ======================== decode ========================

    /**
     * 将 JSON 字节数组解码为指定类型的对象.
     *
     * @param bytes 原始 JSON 字节数组
     * @return 反序列化后的 {@link T} 类型对象
     * @throws NetException 如果字节数组为 {@code null}
     */
    @Override
    public T decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new NetException("Cannot decode null/empty bytes with JsonProtocolCodec");
        }
        return JsonUtil.parseObject(bytes, messageType);
    }

    // ======================== getProtocolName ========================

    @Override
    public String getProtocolName() {
        return "JSON(" + messageType.getSimpleName() + ")";
    }

    // ======================== Getter ========================

    /**
     * 获取此编解码器的目标消息类型.
     *
     * @return 消息类型 Class
     */
    public Class<T> getMessageType() {
        return messageType;
    }
}
