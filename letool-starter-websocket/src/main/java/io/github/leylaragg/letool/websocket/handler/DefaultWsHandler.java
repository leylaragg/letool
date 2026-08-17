package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.websocket.auth.WsHandshakeInterceptor;
import io.github.leylaragg.letool.websocket.config.WebSocketProperties;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsMessageCodec;
import io.github.leylaragg.letool.websocket.core.WsPrincipal;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.core.WsSessionManager;
import io.github.leylaragg.letool.websocket.exception.WsException;
import io.github.leylaragg.letool.websocket.heartbeat.HeartbeatDetector;
import io.github.leylaragg.letool.websocket.room.WsRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * WebSocket 端点的默认连接生命周期处理器。
 */
public class DefaultWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWsHandler.class);

    private final WsSessionManager sessionManager;
    private final WsRoomManager roomManager;
    private final HeartbeatDetector heartbeatDetector;
    private final WsMessageRouter messageRouter;
    private final WsMessageCodec messageCodec;
    private final WsErrorHandler errorHandler;
    private final WebSocketProperties properties;

    /**
     * 创建默认连接处理器。
     *
     * @param sessionManager 会话管理器
     * @param roomManager 房间管理器
     * @param heartbeatDetector 心跳检测器，关闭心跳时为 {@code null}
     * @param messageRouter 消息路由器
     * @param messageCodec 消息编解码器
     * @param errorHandler 错误处理器
     * @param properties WebSocket 配置
     */
    public DefaultWsHandler(
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            HeartbeatDetector heartbeatDetector,
            WsMessageRouter messageRouter,
            WsMessageCodec messageCodec,
            WsErrorHandler errorHandler,
            WebSocketProperties properties) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.heartbeatDetector = heartbeatDetector;
        this.messageRouter = Objects.requireNonNull(messageRouter, "messageRouter must not be null");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec must not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 建立主体绑定会话并注册索引。
     *
     * @param nativeSession Spring 原生会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession nativeSession) {
        WsPrincipal principal = principal(nativeSession);
        if (principal == null) {
            closeNative(nativeSession, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WsSession session = new WsSession(
                nativeSession,
                principal.getUserId(),
                Math.toIntExact(properties.getSendTimeLimit().toMillis()),
                Math.toIntExact(properties.getSendBufferSize().toBytes()));
        session.setAttribute(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
        session.setHeartbeatTimeout(properties.getHeartbeat().getTimeout());
        session.setHeartbeatCheckEnabled(properties.getHeartbeat().isEnabled());
        try {
            sessionManager.register(session);
            if (heartbeatDetector != null) {
                heartbeatDetector.recordHeartbeat(session.getSessionId());
            }
        } catch (WsException exception) {
            session.disconnect(CloseStatus.POLICY_VIOLATION);
            log.warn("WebSocket 连接注册失败，sessionId={}，code={}",
                    session.getSessionId(), exception.getCode());
        }
    }

    /**
     * 校验文本帧并路由业务消息。
     *
     * @param nativeSession Spring 原生会话
     * @param textMessage 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession nativeSession, TextMessage textMessage) {
        WsSession session = sessionManager.getSession(nativeSession.getId());
        if (session == null) {
            closeNative(nativeSession, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (textMessage.getPayload().getBytes(StandardCharsets.UTF_8).length
                > properties.getMaxFrameSize().toBytes()) {
            session.disconnect(CloseStatus.TOO_BIG_TO_PROCESS);
            cleanup(session.getSessionId());
            return;
        }
        WsMessage message = null;
        try {
            message = messageCodec.decode(textMessage.getPayload());
            session.refreshHeartbeat();
            message.setSenderId(session.getUserId());
            if (WsMessage.TYPE_PING.equals(message.getType())) {
                session.sendText(messageCodec.encode(WsMessage.pong()));
                if (heartbeatDetector != null) {
                    heartbeatDetector.recordHeartbeat(session.getSessionId());
                }
                return;
            }
            messageRouter.route(session, message);
        } catch (WsException exception) {
            errorHandler.handle(session, exception, message == null ? null : message.getMessageId());
        }
    }

    /**
     * 处理原生 Pong 帧并刷新活动时间。
     *
     * @param nativeSession Spring 原生会话
     * @param message Pong 消息
     */
    @Override
    public void handlePongMessage(WebSocketSession nativeSession, PongMessage message) {
        WsSession session = sessionManager.getSession(nativeSession.getId());
        if (session != null) {
            session.refreshHeartbeat();
            if (heartbeatDetector != null) {
                heartbeatDetector.recordHeartbeat(session.getSessionId());
            }
        }
    }

    /**
     * 传输异常时执行统一清理。
     *
     * @param nativeSession Spring 原生会话
     * @param exception 传输异常
     */
    @Override
    public void handleTransportError(WebSocketSession nativeSession, Throwable exception) {
        log.warn("WebSocket 传输异常，sessionId={}，type={}",
                nativeSession.getId(), exception.getClass().getSimpleName());
        cleanup(nativeSession.getId());
    }

    /**
     * 连接关闭后执行统一清理。
     *
     * @param nativeSession Spring 原生会话
     * @param status 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession nativeSession, CloseStatus status) {
        cleanup(nativeSession.getId());
    }

    /**
     * 明确拒绝部分消息，避免应用层自行拼接未受限缓冲区。
     *
     * @return 始终返回 {@code false}
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 获取握手阶段注入的主体。
     *
     * @param nativeSession 原生会话
     * @return 用户主体，不存在时返回 {@code null}
     */
    private WsPrincipal principal(WebSocketSession nativeSession) {
        Object value = nativeSession.getAttributes().get(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        return value instanceof WsPrincipal principal ? principal : null;
    }

    /**
     * 清理房间和会话索引。
     *
     * @param sessionId 会话 ID
     */
    private void cleanup(String sessionId) {
        sessionManager.remove(sessionId);
        roomManager.removeSession(sessionId);
    }

    /**
     * 尽力关闭尚未包装的原生会话。
     *
     * @param nativeSession 原生会话
     * @param status 关闭状态
     */
    private void closeNative(WebSocketSession nativeSession, CloseStatus status) {
        try {
            nativeSession.close(status);
        } catch (Exception exception) {
            log.debug("关闭未注册 WebSocket 会话失败，sessionId={}", nativeSession.getId(), exception);
        }
    }
}
