package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.exception.WsException;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于精确消息类型匹配的线程安全默认路由器。
 */
public final class DefaultWsMessageRouter implements WsMessageRouter {

    private final ConcurrentHashMap<String, WsMessageHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 创建空路由器。
     */
    public DefaultWsMessageRouter() {
    }

    /**
     * 创建路由器并注册程序化处理器。
     *
     * @param handlers 程序化处理器集合
     */
    public DefaultWsMessageRouter(Collection<WsMessageHandler> handlers) {
        if (handlers != null) {
            for (WsMessageHandler handler : handlers) {
                register(handler.getMessageType(), handler);
            }
        }
    }

    /**
     * 原子注册唯一消息类型。
     *
     * @param messageType 消息类型
     * @param handler 消息处理器
     */
    @Override
    public void register(String messageType, WsMessageHandler handler) {
        if (messageType == null || messageType.isBlank()) {
            throw WsException.configurationInvalid("消息路由类型不能为空");
        }
        WsMessageHandler requiredHandler = Objects.requireNonNull(handler, "handler must not be null");
        WsMessageHandler existing = handlers.putIfAbsent(messageType, requiredHandler);
        if (existing != null && existing != requiredHandler) {
            throw WsException.routeConflict(messageType);
        }
    }

    /**
     * 路由消息并统一转换处理器异常。
     *
     * @param session 当前会话
     * @param message 入站消息
     */
    @Override
    public void route(WsSession session, WsMessage message) {
        Objects.requireNonNull(session, "session must not be null");
        if (message == null || message.getType() == null || message.getType().isBlank()) {
            throw WsException.invalidMessage("消息类型不能为空");
        }
        WsMessageHandler handler = handlers.get(message.getType());
        if (handler == null) {
            throw WsException.invalidMessage("未注册的消息类型");
        }
        try {
            handler.handle(session, message);
        } catch (WsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw WsException.handlerFailed(message.getType(), exception);
        }
    }

    /**
     * 获取已注册消息类型快照。
     *
     * @return 不可变消息类型集合
     */
    @Override
    public Set<String> getRegisteredMessageTypes() {
        return Set.copyOf(handlers.keySet());
    }
}
