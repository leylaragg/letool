package com.github.leyland.letool.websocket.room;

import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 房间/频道管理器，负责房间的创建、销毁、成员进出以及消息广播。
 *
 * <p>该管理器提供了完整的房间生命周期管理能力：</p>
 * <ul>
 *   <li>创建和删除房间</li>
 *   <li>会话加入/离开房间</li>
 *   <li>查询房间成员</li>
 *   <li>房间内消息广播（支持排除指定发送者）</li>
 *   <li>查询用户所在的所有房间</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} 存储房间映射。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 创建房间
 * roomManager.create("chat:room_1", "聊天室1");
 *
 * // 加入房间
 * roomManager.join("chat:room_1", userSession);
 *
 * // 广播消息到房间（排除发送者）
 * roomManager.broadcast("chat:room_1", message, senderSessionId);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsRoomManager {

    private static final Logger log = LoggerFactory.getLogger(WsRoomManager.class);

    // ======================== 字段 ========================

    /** roomId -> WsRoom 映射 */
    private final ConcurrentHashMap<String, WsRoom> rooms = new ConcurrentHashMap<>();

    /** 关联的会话管理器，用于通过 sessionId 获取 WsSession 进行消息发送 */
    private final WsSessionManager sessionManager;

    // ======================== 构造 ========================

    /**
     * 创建房间管理器。
     *
     * @param sessionManager 会话管理器，用于解析 sessionId 为 WsSession
     */
    public WsRoomManager(WsSessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    // ======================== 房间操作 ========================

    /**
     * 创建房间。如果房间已存在则直接返回已存在的房间。
     *
     * @param roomId 房间唯一标识
     * @param name   房间名称
     * @return 创建或已存在的 WsRoom 实例
     */
    public WsRoom create(String roomId, String name) {
        return rooms.computeIfAbsent(roomId, id -> {
            WsRoom room = new WsRoom(id, name);
            log.info("Room created: {}", room);
            return room;
        });
    }

    /**
     * 删除房间（如果房间为空会自动移除）。
     * 如果房间还有成员，默认不删除，需要设置 force=true 强制删除。
     *
     * @param roomId 房间 ID
     * @param force  是否强制删除（即使还有成员）
     * @return {@code true} 如果房间被删除
     */
    public boolean remove(String roomId, boolean force) {
        WsRoom room = rooms.get(roomId);
        if (room == null) return false;
        if (!room.isEmpty() && !force) {
            log.warn("Cannot remove room {} with {} members, use force=true to remove", roomId, room.getMemberCount());
            return false;
        }
        rooms.remove(roomId);
        log.info("Room removed: {}", roomId);
        return true;
    }

    /**
     * 删除空房间。如果房间还有成员则不会删除。
     *
     * @param roomId 房间 ID
     * @return {@code true} 如果房间被删除，{@code false} 如果房间不存在或仍有成员
     */
    public boolean remove(String roomId) {
        return remove(roomId, false);
    }

    /**
     * 获取房间信息。
     *
     * @param roomId 房间 ID
     * @return 对应的 WsRoom，不存在返回 {@code null}
     */
    public WsRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 检查房间是否存在。
     *
     * @param roomId 房间 ID
     * @return {@code true} 如果房间存在
     */
    public boolean exists(String roomId) {
        return rooms.containsKey(roomId);
    }

    /**
     * 获取所有房间。
     *
     * @return 所有房间的集合视图
     */
    public Collection<WsRoom> getAllRooms() {
        return rooms.values();
    }

    // ======================== 成员操作 ========================

    /**
     * 会话加入房间。
     *
     * @param roomId  房间 ID
     * @param session 待加入的会话
     * @return {@code true} 如果成功加入，{@code false} 如果已在房间中或房间不存在
     */
    public boolean join(String roomId, WsSession session) {
        if (session == null) return false;
        WsRoom room = rooms.get(roomId);
        if (room == null) {
            log.warn("Room {} does not exist, cannot join", roomId);
            return false;
        }
        boolean added = room.addMember(session.getSessionId());
        if (added) {
            log.info("Session {} joined room {}", session.getSessionId(), roomId);
        }
        return added;
    }

    /**
     * 会话离开房间。
     *
     * @param roomId  房间 ID
     * @param session 待离开的会话
     * @return {@code true} 如果成功离开，{@code false} 如果不在房间中或房间不存在
     */
    public boolean leave(String roomId, WsSession session) {
        if (session == null) return false;
        WsRoom room = rooms.get(roomId);
        if (room == null) return false;
        boolean removed = room.removeMember(session.getSessionId());
        if (removed) {
            log.info("Session {} left room {}", session.getSessionId(), roomId);
            // 如果房间变为空，自动清理
            if (room.isEmpty()) {
                rooms.remove(roomId);
                log.info("Room {} auto-removed (no members left)", roomId);
            }
        }
        return removed;
    }

    /**
     * 获取房间内的所有成员会话（已过滤不活跃的会话）。
     *
     * @param roomId 房间 ID
     * @return 成员 WsSession 集合，房间不存在返回空集合
     */
    public Set<WsSession> getMembers(String roomId) {
        WsRoom room = rooms.get(roomId);
        if (room == null) return Collections.emptySet();
        return room.getMembers().stream()
                .map(sessionManager::getSession)
                .filter(Objects::nonNull)
                .filter(WsSession::isAlive)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户所在的所有房间。
     *
     * @param userId 用户 ID
     * @return 该用户所在的所有房间集合
     */
    public Set<WsRoom> getUserRooms(String userId) {
        if (userId == null || userId.isEmpty()) return Collections.emptySet();
        Set<WsSession> userSessions = sessionManager.getUserSessions(userId);
        Set<String> userSessionIds = userSessions.stream()
                .map(WsSession::getSessionId)
                .collect(Collectors.toSet());
        return rooms.values().stream()
                .filter(room -> room.getMembers().stream().anyMatch(userSessionIds::contains))
                .collect(Collectors.toSet());
    }

    // ======================== 消息广播 ========================

    /**
     * 向房间内所有成员广播消息。
     *
     * <p>遍历房间内所有会话，逐个发送消息。发送失败的会话不会被中断后续发送。</p>
     *
     * @param roomId 目标房间 ID
     * @param message 待广播的消息
     */
    public void broadcast(String roomId, WsMessage message) {
        broadcast(roomId, message, null);
    }

    /**
     * 向房间内所有成员广播消息（排除指定会话）。
     *
     * <p>常见于聊天场景：发送者本人的消息已在前端展现，无需服务端再推送一次，
     * 可通过 {@code excludeSessionId} 排除发送者自己的会话。</p>
     *
     * @param roomId           目标房间 ID
     * @param message          待广播的消息
     * @param excludeSessionId 需排除的会话 ID（如发送者本人），{@code null} 时不排除任何会话
     */
    public void broadcast(String roomId, WsMessage message, String excludeSessionId) {
        WsRoom room = rooms.get(roomId);
        if (room == null) {
            log.warn("Room {} does not exist, cannot broadcast", roomId);
            return;
        }
        for (String sid : room.getMembers()) {
            if (Objects.equals(sid, excludeSessionId)) continue;
            WsSession session = sessionManager.getSession(sid);
            if (session != null && session.isAlive()) {
                try {
                    session.sendMessage(message);
                } catch (Exception e) {
                    log.warn("Failed to send message to session {} in room {}: {}", sid, roomId, e.getMessage());
                }
            }
        }
    }

    /**
     * 自动清理不活跃的会话（从所有房间中移除已断开或心跳超时的会话）。
     *
     * <p>建议配合 {@code HeartbeatDetector} 定期调用该方法，
     * 或由心跳检测器在发现超时会话后主动调用 {@link #leave(String, WsSession)}。</p>
     */
    public void cleanupInactiveSessions() {
        for (WsRoom room : rooms.values()) {
            Iterator<String> it = room.getMembers().iterator();
            while (it.hasNext()) {
                String sessionId = it.next();
                WsSession session = sessionManager.getSession(sessionId);
                if (session == null || !session.isAlive()) {
                    it.remove();
                }
            }
            // 移除空房间
            if (room.isEmpty()) {
                rooms.remove(room.getRoomId());
                log.info("Room {} auto-removed during cleanup", room.getRoomId());
            }
        }
    }
}
