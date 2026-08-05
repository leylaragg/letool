package com.github.leyland.letool.websocket.room;

import com.github.leyland.letool.tool.json.Fastjson2JsonCodec;
import com.github.leyland.letool.websocket.core.WsDeliveryResult;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护房间成员和会话反向索引的线程安全管理器。
 */
public class WsRoomManager {

    private static final Logger log = LoggerFactory.getLogger(WsRoomManager.class);
    private static final int LOCK_STRIPE_COUNT = 128;

    private final ConcurrentHashMap<String, WsRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionRooms = new ConcurrentHashMap<>();
    private final Object[] roomLocks = createLocks();
    private final WsSessionManager sessionManager;
    private final com.github.leyland.letool.websocket.core.WsMessageCodec messageCodec;

    /**
     * 创建房间管理器。
     *
     * @param sessionManager 会话管理器
     */
    public WsRoomManager(WsSessionManager sessionManager) {
        this(sessionManager, new com.github.leyland.letool.websocket.core.WsMessageCodec(
                Fastjson2JsonCodec.createDefault()));
    }

    /**
     * 使用指定消息编解码器创建房间管理器。
     *
     * @param sessionManager 会话管理器
     * @param messageCodec 消息编解码器
     */
    public WsRoomManager(
            WsSessionManager sessionManager,
            com.github.leyland.letool.websocket.core.WsMessageCodec messageCodec) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec must not be null");
    }

    /**
     * 创建或查询房间。
     *
     * @param roomId 房间 ID
     * @param name 房间名称
     * @return 房间
     */
    public WsRoom create(String roomId, String name) {
        requireText(roomId, "roomId");
        synchronized (lockFor(roomId)) {
            return rooms.computeIfAbsent(roomId, id -> new WsRoom(id, name));
        }
    }

    /**
     * 删除空房间。
     *
     * @param roomId 房间 ID
     * @return 删除成功时返回 {@code true}
     */
    public boolean remove(String roomId) {
        return remove(roomId, false);
    }

    /**
     * 删除房间并在强制模式下同步反向索引。
     *
     * @param roomId 房间 ID
     * @param force 是否允许删除非空房间
     * @return 删除成功时返回 {@code true}
     */
    public boolean remove(String roomId, boolean force) {
        if (roomId == null) {
            return false;
        }
        synchronized (lockFor(roomId)) {
            WsRoom room = rooms.get(roomId);
            if (room == null || (!force && !room.isEmpty())) {
                return false;
            }
            if (!rooms.remove(roomId, room)) {
                return false;
            }
            for (String sessionId : room.getMembers()) {
                removeReverseIndex(sessionId, roomId);
            }
            return true;
        }
    }

    /**
     * 查询房间。
     *
     * @param roomId 房间 ID
     * @return 房间，不存在时返回 {@code null}
     */
    public WsRoom getRoom(String roomId) {
        return roomId == null ? null : rooms.get(roomId);
    }

    /**
     * 判断房间是否存在。
     *
     * @param roomId 房间 ID
     * @return 房间存在时返回 {@code true}
     */
    public boolean exists(String roomId) {
        return roomId != null && rooms.containsKey(roomId);
    }

    /**
     * 获取全部房间快照。
     *
     * @return 不可变房间快照
     */
    public Collection<WsRoom> getAllRooms() {
        return java.util.List.copyOf(rooms.values());
    }

    /**
     * 将已注册会话加入房间。
     *
     * @param roomId 房间 ID
     * @param session 会话
     * @return 首次加入时返回 {@code true}
     */
    public boolean join(String roomId, WsSession session) {
        Objects.requireNonNull(session, "session must not be null");
        requireText(roomId, "roomId");
        synchronized (lockFor(roomId)) {
            synchronized (session) {
                if (sessionManager.getSession(session.getSessionId()) != session || !session.isAlive()) {
                    throw new IllegalArgumentException("session must be registered before joining a room");
                }
                WsRoom room = rooms.get(roomId);
                if (room == null) {
                    return false;
                }
                boolean added = room.addMember(session.getSessionId());
                if (!added) {
                    return false;
                }
                sessionRooms.computeIfAbsent(session.getSessionId(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(roomId);
                return true;
            }
        }
    }

    /**
     * 将会话移出房间，并自动删除空房间。
     *
     * @param roomId 房间 ID
     * @param session 会话
     * @return 实际移除时返回 {@code true}
     */
    public boolean leave(String roomId, WsSession session) {
        return session != null && leave(roomId, session.getSessionId());
    }

    /**
     * 按会话 ID 离开房间。
     *
     * @param roomId 房间 ID
     * @param sessionId 会话 ID
     * @return 实际移除时返回 {@code true}
     */
    public boolean leave(String roomId, String sessionId) {
        if (roomId == null || sessionId == null) {
            return false;
        }
        synchronized (lockFor(roomId)) {
            WsRoom room = rooms.get(roomId);
            if (room == null || !room.removeMember(sessionId)) {
                return false;
            }
            removeReverseIndex(sessionId, roomId);
            if (room.isEmpty()) {
                rooms.remove(roomId, room);
            }
            return true;
        }
    }

    /**
     * 清理指定会话加入的全部房间。
     *
     * @param sessionId 会话 ID
     */
    public void removeSession(String sessionId) {
        for (String roomId : getSessionRooms(sessionId)) {
            leave(roomId, sessionId);
        }
        if (sessionId != null) {
            sessionRooms.remove(sessionId);
        }
    }

    /**
     * 查询会话加入的房间 ID 快照。
     *
     * @param sessionId 会话 ID
     * @return 不可变房间 ID 集合
     */
    public Set<String> getSessionRooms(String sessionId) {
        Set<String> roomIds = sessionId == null ? null : sessionRooms.get(sessionId);
        return roomIds == null ? Set.of() : Set.copyOf(roomIds);
    }

    /**
     * 查询房间内仍存在的会话快照。
     *
     * @param roomId 房间 ID
     * @return 不可变会话集合
     */
    public Set<WsSession> getMembers(String roomId) {
        WsRoom room = rooms.get(roomId);
        if (room == null) {
            return Set.of();
        }
        Set<WsSession> result = new LinkedHashSet<>();
        for (String sessionId : room.getMembers()) {
            WsSession session = sessionManager.getSession(sessionId);
            if (session != null) {
                result.add(session);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * 查询用户会话加入的全部房间。
     *
     * @param userId 用户标识
     * @return 不可变房间集合
     */
    public Set<WsRoom> getUserRooms(String userId) {
        Set<WsRoom> result = new LinkedHashSet<>();
        for (WsSession session : sessionManager.getUserSessions(userId)) {
            for (String roomId : getSessionRooms(session.getSessionId())) {
                WsRoom room = rooms.get(roomId);
                if (room != null) {
                    result.add(room);
                }
            }
        }
        return Set.copyOf(result);
    }

    /**
     * 向房间全部成员广播消息。
     *
     * @param roomId 房间 ID
     * @param message 消息
     * @return 投递结果
     */
    public WsDeliveryResult broadcast(String roomId, WsMessage message) {
        return broadcast(roomId, message, null);
    }

    /**
     * 向房间成员广播消息并排除指定会话。
     *
     * @param roomId 房间 ID
     * @param message 消息
     * @param excludeSessionId 排除的会话 ID
     * @return 投递结果
     */
    public WsDeliveryResult broadcast(String roomId, WsMessage message, String excludeSessionId) {
        WsRoom room = rooms.get(roomId);
        if (room == null) {
            return WsDeliveryResult.empty();
        }
        int targets = 0;
        int successes = 0;
        int failures = 0;
        int stale = 0;
        for (String sessionId : room.getMembers()) {
            if (Objects.equals(sessionId, excludeSessionId)) {
                continue;
            }
            targets++;
            WsSession session = sessionManager.getSession(sessionId);
            if (session == null || !session.isAlive()) {
                stale++;
                continue;
            }
            try {
                session.sendText(messageCodec.encode(message));
                successes++;
            } catch (RuntimeException exception) {
                failures++;
                log.warn("房间消息投递失败，roomId={}，sessionId={}，type={}",
                        roomId, sessionId, exception.getClass().getSimpleName());
            }
        }
        return new WsDeliveryResult(targets, successes, failures, stale);
    }

    /**
     * 清理当前已经失效的会话成员。
     */
    public void cleanupInactiveSessions() {
        for (WsRoom room : java.util.List.copyOf(rooms.values())) {
            for (String sessionId : room.getMembers()) {
                WsSession session = sessionManager.getSession(sessionId);
                if (session == null || !session.isAlive()) {
                    leave(room.getRoomId(), sessionId);
                }
            }
        }
    }

    /**
     * 删除会话反向索引。
     *
     * @param sessionId 会话 ID
     * @param roomId 房间 ID
     */
    private void removeReverseIndex(String sessionId, String roomId) {
        sessionRooms.computeIfPresent(sessionId, (ignored, roomIds) -> {
            roomIds.remove(roomId);
            return roomIds.isEmpty() ? null : roomIds;
        });
    }

    /**
     * 校验必填文本。
     *
     * @param value 文本值
     * @param name 参数名称
     */
    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * 获取房间对应的稳定分段锁。
     *
     * @param roomId 房间 ID
     * @return 分段锁
     */
    private Object lockFor(String roomId) {
        return roomLocks[(roomId.hashCode() & Integer.MAX_VALUE) % roomLocks.length];
    }

    /**
     * 创建固定数量的房间分段锁，避免房间动态创建导致锁对象无限增长。
     *
     * @return 分段锁数组
     */
    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_STRIPE_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }
}
