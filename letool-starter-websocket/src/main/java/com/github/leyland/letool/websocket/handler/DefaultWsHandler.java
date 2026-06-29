package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.tool.util.StrUtil;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.heartbeat.HeartbeatDetector;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 WebSocket 连接处理器，继承自 Spring 的 {@link TextWebSocketHandler}。
 *
 * <p>该处理器是 letool WebSocket 模块的核心入口，负责处理 WebSocket 连接生命周期中的
 * 所有事件，包括连接建立、消息收发、传输异常和连接关闭。</p>
 *
 * <p><b>处理流程：</b></p>
 * <ol>
 *   <li>连接建立（{@code afterConnectionEstablished}）— 创建 WsSession，注册到 SessionManager，
 *       提取握手阶段注入的 WsPrincipal，记录心跳</li>
 *   <li>消息到达（{@code handleTextMessage}）— 将 JSON 文本解析为 WsMessage，
 *       处理心跳消息（ping），将业务消息分发给匹配的 WsMessageHandler</li>
 *   <li>连接关闭（{@code afterConnectionClosed}）— 清理房间成员关系，注销会话</li>
 *   <li>传输异常（{@code handleTransportError}）— 记录错误日志，清理会话</li>
 * </ol>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DefaultWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultWsHandler.class);

    // ======================== 字段 ========================

    /** 会话管理器 */
    private final WsSessionManager sessionManager;

    /** 房间管理器 */
    private final WsRoomManager roomManager;

    /** 心跳检测器（可选） */
    private final HeartbeatDetector heartbeatDetector;

    /** 消息处理器注册表（type -> handler） */
    private final Map<String, WsMessageHandler> handlerRegistry = new HashMap<>();

    // ======================== 构造 ========================

    /**
     * 创建默认 WebSocket 处理器。
     *
     * @param sessionManager    会话管理器
     * @param roomManager       房间管理器
     * @param heartbeatDetector 心跳检测器（可为 {@code null}）
     * @param handlers          消息处理器列表（可为空）
     */
    public DefaultWsHandler(WsSessionManager sessionManager,
                            WsRoomManager roomManager,
                            HeartbeatDetector heartbeatDetector,
                            List<WsMessageHandler> handlers) {
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
        this.heartbeatDetector = heartbeatDetector;
        if (handlers != null) {
            for (WsMessageHandler handler : handlers) {
                handlerRegistry.put(handler.getMessageType(), handler);
                log.info("Registered WsMessageHandler: {} -> {}", handler.getMessageType(), handler.getClass().getSimpleName());
            }
        }
    }

    // ======================== 连接建立 ========================

    /**
     * WebSocket 连接建立后的回调。
     *
     * <p>处理步骤：</p>
     * <ol>
     *   <li>创建 WsSession 包装原生会话</li>
     *   <li>提取握手阶段注入的 WsPrincipal，绑定 userId</li>
     *   <li>将会话注册到 SessionManager</li>
     *   <li>记录初次心跳</li>
     * </ol>
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @throws Exception 处理异常
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession nativeSession) throws Exception {
        WsSession session = new WsSession(nativeSession);

        // 提取握手阶段注入的 WsPrincipal
        WsPrincipal principal = getPrincipalFromAttributes(nativeSession);
        if (principal != null) {
            session.setUserId(principal.getUserId());
            session.setAttribute("principal", principal);
        }

        // 注册会话
        sessionManager.register(session);

        // 记录心跳
        if (heartbeatDetector != null) {
            heartbeatDetector.recordHeartbeat(session.getSessionId());
        }

        log.info("WebSocket connection established: sessionId={}, remoteAddress={}",
                session.getSessionId(),
                nativeSession.getRemoteAddress() != null ? nativeSession.getRemoteAddress() : "unknown");

        // 发送连接成功消息
        WsMessage welcome = WsMessage.builder()
                .type(WsMessage.TYPE_NOTIFICATION)
                .payload("连接成功")
                .build();
        session.sendMessage(welcome);
    }

    // ======================== 消息处理 ========================

    /**
     * 收到文本消息时的回调。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>获取对应的 WsSession</li>
     *   <li>将 JSON 文本解析为 WsMessage</li>
     *   <li>如果是心跳消息（ping），回复 pong 并刷新心跳</li>
     *   <li>否则查找匹配的 WsMessageHandler 进行分发</li>
     * </ol>
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @param message       收到的文本消息
     * @throws Exception 处理异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession nativeSession, TextMessage message) throws Exception {
        WsSession session = findSession(nativeSession);
        if (session == null) {
            log.warn("Message received from unregistered session, ignoring");
            return;
        }

        String payload = message.getPayload();
        if (StrUtil.isBlank(payload)) {
            log.warn("Empty message received from session {}", session.getSessionId());
            return;
        }

        // 尝试解析为 WsMessage
        WsMessage wsMessage;
        try {
            wsMessage = JsonUtil.parseObject(payload, WsMessage.class);
        } catch (Exception e) {
            log.warn("Failed to parse message from session {}: {}", session.getSessionId(), e.getMessage());
            // 回退：当作纯文本消息处理
            wsMessage = WsMessage.text(payload);
        }

        if (wsMessage == null || wsMessage.getType() == null) {
            log.warn("Message type is null from session {}", session.getSessionId());
            return;
        }

        // 设置发送者 ID
        wsMessage.setSenderId(session.getUserId());

        // 处理心跳消息
        if (WsMessage.TYPE_PING.equals(wsMessage.getType())) {
            session.sendMessage(WsMessage.pong());
            if (heartbeatDetector != null) {
                heartbeatDetector.recordHeartbeat(session.getSessionId());
            }
            session.refreshHeartbeat();
            return;
        }

        // 分发给对应的消息处理器
        WsMessageHandler handler = handlerRegistry.get(wsMessage.getType());
        if (handler != null) {
            try {
                handler.handle(session, wsMessage);
            } catch (Exception e) {
                log.error("Error handling message type={} from session={}: {}",
                        wsMessage.getType(), session.getSessionId(), e.getMessage(), e);
                session.sendMessage(WsMessage.error("消息处理异常: " + e.getMessage()));
            }
        } else {
            log.debug("No handler registered for message type: {}", wsMessage.getType());
        }
    }

    // ======================== 传输异常 ========================

    /**
     * WebSocket 传输异常回调。
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @param exception     发生的异常
     * @throws Exception 处理异常
     */
    @Override
    public void handleTransportError(WebSocketSession nativeSession, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}",
                nativeSession.getId(), exception.getMessage(), exception);

        // 尝试清理会话
        cleanupSession(nativeSession);
    }

    // ======================== 连接关闭 ========================

    /**
     * WebSocket 连接关闭后的回调。
     *
     * <p>清理工作：</p>
     * <ol>
     *   <li>遍历所有房间，将该会话从房间中移除</li>
     *   <li>从 SessionManager 中注销会话</li>
     * </ol>
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @param status        关闭状态码和原因
     * @throws Exception 处理异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession nativeSession, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: sessionId={}, status={}",
                nativeSession.getId(), status);

        // 从所有房间移除
        if (roomManager != null) {
            WsSession session = findSession(nativeSession);
            if (session != null) {
                for (String roomId : roomManager.getUserRooms(session.getUserId()).stream()
                        .map(r -> r.getRoomId()).toList()) {
                    roomManager.leave(roomId, session);
                }
            }
        }

        // 注销会话
        cleanupSession(nativeSession);
    }

    // ======================== 辅助方法 ========================

    /**
     * 支持部分消息（分帧传输）。本项目不支持，返回 {@code false}。
     *
     * @return 始终返回 {@code false}
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 根据原生 WebSocket 会话查找对应的 WsSession。
     *
     * <p>通过原生 session 的 ID 在 sessionManager 中查找。</p>
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @return 对应的 WsSession，未注册返回 {@code null}
     */
    private WsSession findSession(WebSocketSession nativeSession) {
        if (nativeSession == null) return null;
        // 遍历查找：原生 session id 与 WsSession 不一定直接对应
        for (WsSession s : sessionManager.getAllSessions()) {
            if (nativeSession.getId().equals(s.getNativeSession().getId())) {
                return s;
            }
        }
        return null;
    }

    /**
     * 从原生会话属性中提取 WsPrincipal。
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @return WsPrincipal 实例，不存在返回 {@code null}
     */
    private WsPrincipal getPrincipalFromAttributes(WebSocketSession nativeSession) {
        Object obj = nativeSession.getAttributes().get("principal");
        if (obj instanceof WsPrincipal) {
            return (WsPrincipal) obj;
        }
        return null;
    }

    /**
     * 清理会话：从 SessionManager 中移除。
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     */
    private void cleanupSession(WebSocketSession nativeSession) {
        WsSession session = findSession(nativeSession);
        if (session != null) {
            sessionManager.remove(session.getSessionId());
        }
    }
}
