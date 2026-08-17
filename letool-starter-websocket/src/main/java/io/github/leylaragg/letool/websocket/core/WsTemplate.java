package io.github.leylaragg.letool.websocket.core;

import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.websocket.room.WsRoomManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 提供按会话、用户、房间和全部连接推送消息的便利门面。
 *
 * <p>所有结果仅代表当前进程内的实际连接，不表示跨节点投递成功。</p>
 */
public class WsTemplate {

    private final WsSessionManager sessionManager;
    private final WsRoomManager roomManager;
    private final WsMessageCodec messageCodec;

    /**
     * 创建消息推送门面。
     *
     * @param sessionManager 会话管理器
     * @param roomManager 房间管理器
     */
    public WsTemplate(WsSessionManager sessionManager, WsRoomManager roomManager) {
        this(sessionManager, roomManager, new WsMessageCodec(Fastjson2JsonCodec.createDefault()));
    }

    /**
     * 使用指定消息编解码器创建消息推送门面。
     *
     * @param sessionManager 会话管理器
     * @param roomManager 房间管理器
     * @param messageCodec 消息编解码器
     */
    public WsTemplate(
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            WsMessageCodec messageCodec) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec must not be null");
    }

    /**
     * 向指定用户全部会话推送负载。
     *
     * @param userId 用户标识
     * @param payload 消息负载
     * @return 投递结果
     */
    public WsDeliveryResult sendToUser(String userId, Object payload) {
        return sendToUser(userId, buildMessage(payload));
    }

    /**
     * 向指定用户全部会话推送消息。
     *
     * @param userId 用户标识
     * @param message 消息
     * @return 投递结果
     */
    public WsDeliveryResult sendToUser(String userId, WsMessage message) {
        return deliver(sessionManager.getUserSessions(userId), message, null);
    }

    /**
     * 向指定会话推送负载。
     *
     * @param sessionId 会话 ID
     * @param payload 消息负载
     * @return 投递结果
     */
    public WsDeliveryResult sendToSession(String sessionId, Object payload) {
        return sendToSession(sessionId, buildMessage(payload));
    }

    /**
     * 向指定会话推送消息。
     *
     * @param sessionId 会话 ID
     * @param message 消息
     * @return 投递结果
     */
    public WsDeliveryResult sendToSession(String sessionId, WsMessage message) {
        WsSession session = sessionManager.getSession(sessionId);
        return deliver(session == null ? List.of() : List.of(session), message, null);
    }

    /**
     * 向房间广播负载。
     *
     * @param roomId 房间 ID
     * @param payload 消息负载
     * @return 投递结果
     */
    public WsDeliveryResult sendToRoom(String roomId, Object payload) {
        return sendToRoom(roomId, buildMessage(payload), null);
    }

    /**
     * 向房间广播负载并排除会话。
     *
     * @param roomId 房间 ID
     * @param payload 消息负载
     * @param excludeSessionId 排除的会话 ID
     * @return 投递结果
     */
    public WsDeliveryResult sendToRoom(String roomId, Object payload, String excludeSessionId) {
        return sendToRoom(roomId, buildMessage(payload), excludeSessionId);
    }

    /**
     * 向房间广播消息。
     *
     * @param roomId 房间 ID
     * @param message 消息
     * @return 投递结果
     */
    public WsDeliveryResult sendToRoom(String roomId, WsMessage message) {
        return sendToRoom(roomId, message, null);
    }

    /**
     * 向房间广播消息并排除会话。
     *
     * @param roomId 房间 ID
     * @param message 消息
     * @param excludeSessionId 排除的会话 ID
     * @return 投递结果
     */
    public WsDeliveryResult sendToRoom(
            String roomId,
            WsMessage message,
            String excludeSessionId) {
        return roomManager.broadcast(roomId, message, excludeSessionId);
    }

    /**
     * 向全部会话广播负载。
     *
     * @param payload 消息负载
     * @return 投递结果
     */
    public WsDeliveryResult sendToAll(Object payload) {
        return sendToAll(buildMessage(payload), null);
    }

    /**
     * 向符合条件的会话广播负载。
     *
     * @param payload 消息负载
     * @param filter 会话过滤器
     * @return 投递结果
     */
    public WsDeliveryResult sendToAll(Object payload, Predicate<WsSession> filter) {
        return sendToAll(buildMessage(payload), filter);
    }

    /**
     * 向符合条件的会话广播消息。
     *
     * @param message 消息
     * @param filter 会话过滤器
     * @return 投递结果
     */
    public WsDeliveryResult sendToAll(WsMessage message, Predicate<WsSession> filter) {
        return deliver(sessionManager.getAllSessions(), message, filter);
    }

    /**
     * 获取会话管理器。
     *
     * @return 会话管理器
     */
    public WsSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取房间管理器。
     *
     * @return 房间管理器
     */
    public WsRoomManager getRoomManager() {
        return roomManager;
    }

    /**
     * 向会话集合发送消息并统计结果。
     *
     * @param sessions 目标会话
     * @param message 消息
     * @param filter 可选过滤器
     * @return 投递结果
     */
    private WsDeliveryResult deliver(
            Collection<WsSession> sessions,
            WsMessage message,
            Predicate<WsSession> filter) {
        Objects.requireNonNull(message, "message must not be null");
        List<WsSession> targets = new ArrayList<>();
        for (WsSession session : sessions) {
            if (filter == null || filter.test(session)) {
                targets.add(session);
            }
        }
        int successes = 0;
        int failures = 0;
        int stale = 0;
        for (WsSession session : targets) {
            if (!session.isAlive()) {
                stale++;
                continue;
            }
            try {
                session.sendText(messageCodec.encode(message));
                successes++;
            } catch (RuntimeException exception) {
                failures++;
            }
        }
        return new WsDeliveryResult(targets.size(), successes, failures, stale);
    }

    /**
     * 将业务负载包装为消息信封。
     *
     * @param payload 业务负载
     * @return 消息信封
     */
    private WsMessage buildMessage(Object payload) {
        if (payload instanceof WsMessage message) {
            return message;
        }
        if (payload instanceof String text) {
            return WsMessage.text(text);
        }
        return messageCodec.create(WsMessage.TYPE_NOTIFICATION, payload);
    }
}
