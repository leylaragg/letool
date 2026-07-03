package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.websocket.exception.WsException;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * WebSocket 消息发送模板，是 letool WebSocket 模块的主要入口类。
 *
 * <p>该类封装了向特定用户、会话、房间或全体广播发送消息的便捷方法，
 * 内部自动完成负载序列化、消息包装和通过 {@link WebSocketSession} 发送的完整流程。</p>
 *
 * <p>核心发送能力：</p>
 * <ul>
 *   <li>{@code sendToUser} — 定向推送给指定用户（该用户的所有活跃会话）</li>
 *   <li>{@code sendToSession} — 定向推送给指定会话</li>
 *   <li>{@code sendToRoom} — 房间广播（支持排除发送者）</li>
 *   <li>{@code sendToAll} — 全量广播（支持条件过滤）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 注入 WsTemplate
 * @Autowired
 * private WsTemplate wsTemplate;
 *
 * // 定向推送给用户
 * wsTemplate.sendToUser("user123", "您的订单已发货");
 *
 * // 房间广播排除发送者
 * wsTemplate.sendToRoom("chat:room_1", chatMsg, senderSessionId);
 *
 * // 全量广播，排除离线用户
 * wsTemplate.sendToAll(notification, session -> session.isAlive());
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsTemplate {

    private static final Logger log = LoggerFactory.getLogger(WsTemplate.class);

    // ======================== 字段 ========================

    /** 会话管理器，提供会话查询能力 */
    private final WsSessionManager sessionManager;

    /** 房间管理器，提供房间成员查询和广播能力 */
    private final WsRoomManager roomManager;

    // ======================== 构造 ========================

    /**
     * 创建 WebSocket 消息模板。
     *
     * @param sessionManager 会话管理器 Bean
     */
    public WsTemplate(WsSessionManager sessionManager) {
        this(sessionManager, null);
    }

    /**
     * 创建支持房间广播的 WebSocket 消息模板。
     *
     * @param sessionManager 会话管理器 Bean
     * @param roomManager    房间管理器 Bean，用于按房间成员范围广播
     */
    public WsTemplate(WsSessionManager sessionManager, WsRoomManager roomManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.roomManager = roomManager;
    }

    // ======================== 定向推送 ========================

    /**
     * 向指定用户的所有活跃会话发送消息。
     *
     * <p>负载对象会被自动序列化为 JSON 字符串并包装为 {@link WsMessage}。</p>
     *
     * @param userId  目标用户 ID
     * @param payload 消息负载（任意 Java 对象，自动序列化）
     */
    public void sendToUser(String userId, Object payload) {
        if (userId == null || userId.isEmpty()) {
            log.warn("Cannot send to null/empty userId");
            return;
        }
        WsMessage message = buildMessage(payload);
        for (WsSession session : sessionManager.getUserSessions(userId)) {
            sendToSessionInternal(session, message);
        }
    }

    /**
     * 向指定用户的所有活跃会话发送消息。
     *
     * @param userId  目标用户 ID
     * @param message 预构建的 WsMessage 对象
     */
    public void sendToUser(String userId, WsMessage message) {
        if (userId == null || userId.isEmpty()) {
            log.warn("Cannot send to null/empty userId");
            return;
        }
        for (WsSession session : sessionManager.getUserSessions(userId)) {
            sendToSessionInternal(session, message);
        }
    }

    /**
     * 向指定会话发送消息。
     *
     * @param sessionId 目标会话 ID
     * @param payload   消息负载（任意 Java 对象，自动序列化）
     */
    public void sendToSession(String sessionId, Object payload) {
        if (sessionId == null) {
            log.warn("Cannot send to null sessionId");
            return;
        }
        WsSession session = sessionManager.getSession(sessionId);
        if (session != null && session.isAlive()) {
            sendToSessionInternal(session, buildMessage(payload));
        } else {
            log.warn("Session {} not found or not alive", sessionId);
        }
    }

    /**
     * 向指定会话发送预构建的消息。
     *
     * @param sessionId 目标会话 ID
     * @param message   预构建的 WsMessage 对象
     */
    public void sendToSession(String sessionId, WsMessage message) {
        if (sessionId == null) {
            log.warn("Cannot send to null sessionId");
            return;
        }
        WsSession session = sessionManager.getSession(sessionId);
        if (session != null && session.isAlive()) {
            sendToSessionInternal(session, message);
        } else {
            log.warn("Session {} not found or not alive", sessionId);
        }
    }

    // ======================== 房间广播 ========================

    /**
     * 向指定房间内的所有成员广播消息。
     *
     * @param roomId 目标房间 ID
     * @param payload 消息负载
     */
    public void sendToRoom(String roomId, Object payload) {
        sendToRoom(roomId, buildMessage(payload), null);
    }

    /**
     * 向指定房间内的所有成员广播消息（排除指定会话）。
     *
     * @param roomId           目标房间 ID
     * @param payload          消息负载
     * @param excludeSessionId 需排除的会话 ID（如发送者），{@code null} 时不排除
     */
    public void sendToRoom(String roomId, Object payload, String excludeSessionId) {
        sendToRoom(roomId, buildMessage(payload), excludeSessionId);
    }

    /**
     * 向指定房间内的所有成员广播预构建的消息。
     *
     * @param roomId  目标房间 ID
     * @param message 预构建的 WsMessage 对象
     */
    public void sendToRoom(String roomId, WsMessage message) {
        sendToRoom(roomId, message, null);
    }

    /**
     * 向指定房间内的所有成员广播预构建的消息（排除指定会话）。
     *
     * @param roomId           目标房间 ID
     * @param message          预构建的 WsMessage 对象
     * @param excludeSessionId 需排除的会话 ID（如发送者），{@code null} 时不排除
     */
    public void sendToRoom(String roomId, WsMessage message, String excludeSessionId) {
        if (roomId == null) {
            log.warn("Cannot send to null roomId");
            return;
        }
        if (roomManager == null) {
            log.warn("Cannot send to room {} because WsRoomManager is not configured", roomId);
            return;
        }
        roomManager.broadcast(roomId, message, excludeSessionId);
    }

    // ======================== 全量广播 ========================

    /**
     * 向所有在线会话广播消息。
     *
     * @param payload 消息负载
     */
    public void sendToAll(Object payload) {
        sendToAll(payload, null);
    }

    /**
     * 向所有在线会话广播消息（支持条件过滤）。
     *
     * <p>通过 {@code filter} 参数可以按需筛选目标会话，例如只推送给特定角色的用户：</p>
     * <pre>{@code
     * wsTemplate.sendToAll(msg, session -> {
     *     WsPrincipal p = session.getAttribute("principal");
     *     return p != null && p.hasRole("admin");
     * });
     * }</pre>
     *
     * @param payload 消息负载
     * @param filter  会话过滤条件，{@code null} 表示不过滤
     */
    public void sendToAll(Object payload, Predicate<WsSession> filter) {
        WsMessage message = buildMessage(payload);
        sendToAll(message, filter);
    }

    /**
     * 向所有在线会话广播预构建的消息（支持条件过滤）。
     *
     * @param message 预构建的 WsMessage 对象
     * @param filter  会话过滤条件，{@code null} 表示不过滤
     */
    public void sendToAll(WsMessage message, Predicate<WsSession> filter) {
        Collection<WsSession> allSessions = sessionManager.getAllSessions();
        for (WsSession session : allSessions) {
            if (session.isAlive() && (filter == null || filter.test(session))) {
                sendToSessionInternal(session, message);
            }
        }
    }

    // ======================== 内部辅助 ========================

    /**
     * 内部发送方法：将消息通过指定会话发出。
     *
     * @param session 目标会话
     * @param message 待发送的消息
     */
    private void sendToSessionInternal(WsSession session, WsMessage message) {
        try {
            session.sendMessage(message);
        } catch (WsException e) {
            log.error("Failed to send message to session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    /**
     * 根据负载对象构建 WsMessage。
     * 如果 {@code payload} 已经是 WsMessage 实例则直接返回。
     *
     * @param payload 消息负载
     * @return 包装后的 WsMessage
     */
    private WsMessage buildMessage(Object payload) {
        if (payload instanceof WsMessage) {
            return (WsMessage) payload;
        }
        if (payload instanceof String) {
            return WsMessage.text((String) payload);
        }
        return WsMessage.of(WsMessage.TYPE_NOTIFICATION, payload);
    }

    /**
     * 获取关联的会话管理器。
     *
     * @return WsSessionManager 实例
     */
    public WsSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取关联的房间管理器。
     *
     * @return WsRoomManager 实例；使用兼容构造器创建时可能为 {@code null}
     */
    public WsRoomManager getRoomManager() {
        return roomManager;
    }
}
