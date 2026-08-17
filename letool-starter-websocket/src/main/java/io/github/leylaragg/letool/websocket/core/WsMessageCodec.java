package io.github.leylaragg.letool.websocket.core;

import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.websocket.exception.WsException;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 使用可替换 {@link JsonCodec} 处理 WebSocket 消息信封和负载的编解码器。
 */
public final class WsMessageCodec {

    private final JsonCodec jsonCodec;

    /**
     * 创建消息编解码器。
     *
     * @param jsonCodec JSON 编解码扩展
     */
    public WsMessageCodec(JsonCodec jsonCodec) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
    }

    /**
     * 解码并校验入站消息。
     *
     * @param json JSON 消息文本
     * @return 合法消息
     * @throws WsException JSON 非法或消息类型为空时抛出
     */
    public WsMessage decode(String json) {
        try {
            WsMessage message = jsonCodec.read(json, WsMessage.class);
            if (message == null || message.getType() == null || message.getType().isBlank()) {
                throw WsException.invalidMessage("消息类型不能为空");
            }
            return message;
        } catch (WsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw WsException.invalidMessage("JSON 格式错误");
        }
    }

    /**
     * 编码消息信封。
     *
     * @param message 消息
     * @return JSON 文本
     */
    public String encode(WsMessage message) {
        return jsonCodec.write(Objects.requireNonNull(message, "message must not be null"));
    }

    /**
     * 创建使用当前 JSON 方案编码负载的消息。
     *
     * @param type 消息类型
     * @param payload 业务负载
     * @return 消息
     */
    public WsMessage create(String type, Object payload) {
        if (type == null || type.isBlank()) {
            throw WsException.invalidMessage("消息类型不能为空");
        }
        return new WsMessage(type, payload instanceof String text ? text : jsonCodec.write(payload));
    }

    /**
     * 将消息负载解码为指定类型。
     *
     * @param message 消息
     * @param targetType 目标类型
     * @param <T> 返回类型
     * @return 解码结果
     */
    public <T> T readPayload(WsMessage message, Type targetType) {
        return jsonCodec.read(Objects.requireNonNull(message, "message must not be null").getPayload(), targetType);
    }
}
