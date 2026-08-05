package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.tool.annotation.SPI;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.exception.WsException;

/**
 * WebSocket 入站消息错误处理扩展接口。
 */
@SPI
@FunctionalInterface
public interface WsErrorHandler {

    /**
     * 处理可恢复的消息异常。
     *
     * @param session 当前会话
     * @param exception 结构化异常
     * @param requestMessageId 关联入站消息 ID，可为 {@code null}
     */
    void handle(WsSession session, WsException exception, String requestMessageId);
}
