package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.websocket.core.WsErrorPayload;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsMessageCodec;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 发送稳定错误码且不暴露底层异常信息的默认错误处理器。
 */
public final class DefaultWsErrorHandler implements WsErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWsErrorHandler.class);

    private final WsMessageCodec messageCodec;

    /**
     * 创建默认错误处理器。
     *
     * @param messageCodec 消息编解码器
     */
    public DefaultWsErrorHandler(WsMessageCodec messageCodec) {
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec must not be null");
    }

    /**
     * 向仍然打开的连接发送安全错误帧。
     *
     * @param session 当前会话
     * @param exception 结构化异常
     * @param requestMessageId 关联入站消息 ID
     */
    @Override
    public void handle(WsSession session, WsException exception, String requestMessageId) {
        log.warn("WebSocket 消息处理失败，sessionId={}，code={}，type={}",
                session.getSessionId(), exception.getCode(), exception.getClass().getSimpleName());
        log.debug("WebSocket 消息处理失败详情，sessionId={}", session.getSessionId(), exception);
        if (!session.isOpen()) {
            return;
        }
        WsErrorPayload payload = new WsErrorPayload(
                exception.getCode(), exception.getMessage(), requestMessageId);
        WsMessage errorMessage = messageCodec.create(WsMessage.TYPE_ERROR, payload);
        try {
            session.sendText(messageCodec.encode(errorMessage));
        } catch (RuntimeException deliveryException) {
            log.debug("WebSocket 错误帧发送失败，sessionId={}", session.getSessionId(), deliveryException);
        }
    }
}
