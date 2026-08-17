package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.tool.annotation.SPI;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;

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
