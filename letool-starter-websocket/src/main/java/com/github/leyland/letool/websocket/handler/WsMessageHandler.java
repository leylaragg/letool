package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;

/**
 * WebSocket 消息处理器接口，用于处理特定类型的消息。
 *
 * <p>每个实现类通过 {@link #getMessageType()} 声明自己能处理的消息类型，
 * 当 {@link DefaultWsHandler} 收到客户端消息后，会根据消息的 {@code type} 字段
 * 将消息分发给匹配的处理器。</p>
 *
 * <p>实现步骤：</p>
 * <ol>
 *   <li>创建一个类实现 {@code WsMessageHandler} 接口</li>
 *   <li>在 {@code getMessageType()} 中返回要处理的消息类型（如 "chat"、"subscribe" 等）</li>
 *   <li>在 {@code handle()} 中编写具体的业务逻辑</li>
 *   <li>将实现类注册为 Spring Bean，框架会自动发现并注入到 DefaultWsHandler</li>
 * </ol>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Component
 * public class ChatMessageHandler implements WsMessageHandler {
 *     @Override
 *     public String getMessageType() {
 *         return "chat";
 *     }
 *
 *     @Override
 *     public void handle(WsSession session, WsMessage message) {
 *         // 处理聊天消息...
 *     }
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface WsMessageHandler {

    /**
     * 处理收到的 WebSocket 消息。
     *
     * @param session 发送该消息的会话
     * @param message 收到的消息对象
     */
    void handle(WsSession session, WsMessage message);

    /**
     * 获取该处理器能处理的消息类型。
     *
     * <p>返回值与 {@link WsMessage#getType()} 进行精确匹配。</p>
     *
     * @return 消息类型字符串，如 "chat"、"subscribe"、"ping" 等
     */
    String getMessageType();

    /**
     * 获取处理器的描述信息，用于日志或监控。
     *
     * @return 描述文本，默认返回消息类型
     */
    default String getDescription() {
        return getMessageType();
    }
}
