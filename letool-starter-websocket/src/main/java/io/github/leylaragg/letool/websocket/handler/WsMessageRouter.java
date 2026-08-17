package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.tool.annotation.SPI;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;

import java.util.Set;

/**
 * WebSocket 消息类型路由扩展接口。
 */
@SPI
public interface WsMessageRouter {

    /**
     * 注册消息处理器。
     *
     * @param messageType 消息类型
     * @param handler 消息处理器
     * @throws WsException 消息类型重复或非法时抛出
     */
    void register(String messageType, WsMessageHandler handler);

    /**
     * 路由一条入站消息。
     *
     * @param session 当前会话
     * @param message 入站消息
     * @throws WsException 路由不存在、授权失败或处理器失败时抛出
     */
    void route(WsSession session, WsMessage message);

    /**
     * 获取已注册消息类型快照。
     *
     * @return 不可变消息类型集合
     */
    Set<String> getRegisteredMessageTypes();
}
