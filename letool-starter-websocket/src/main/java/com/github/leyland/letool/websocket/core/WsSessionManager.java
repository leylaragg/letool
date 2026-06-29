package com.github.leyland.letool.websocket.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 会话生命周期管理器，负责会话的注册、注销、查询和强制下线。
 *
 * <p>该管理器维护两个核心索引：</p>
 * <ul>
 *   <li>{@code sessions} — sessionId -&gt; WsSession，用于按会话 ID 快速定位</li>
 *   <li>{@code userSessions} — userId -&gt; Set&lt;sessionId&gt;，用于按用户 ID 查找其所有会话</li>
 * </ul>
 *
 * <p>线程安全：所有集合操作均使用 {@link ConcurrentHashMap}，保证并发场景下的数据一致性。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 注册新会话
 * sessionManager.register(session);
 *
 * // 查询某用户的所有会话
 * Set<WsSession> userSessions = sessionManager.getUserSessions("user123");
 *
 * // 强制踢出某会话
 * sessionManager.kickOut(sessionId);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WsSessionManager.class);

    // ======================== 索引容器 ========================

    /** sessionId -> WsSession 映射 */
    private final ConcurrentHashMap<String, WsSession> sessions = new ConcurrentHashMap<>();

    /** userId -> Set<sessionId> 映射（一个用户可能同时打开多个连接） */
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    // ======================== 会话注册与注销 ========================

    /**
     * 注册新会话。
     *
     * <p>将新建立的 WebSocket 会话加入 {@code sessions} 索引，如果会话绑定了用户 ID，
     * 则同时更新 {@code userSessions} 索引。</p>
     *
     * @param session 待注册的会话，不可为 {@code null}
     */
    public void register(WsSession session) {
        Objects.requireNonNull(session, "session must not be null");
        sessions.put(session.getSessionId(), session);
        String userId = session.getUserId();
        if (userId != null && !userId.isEmpty()) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getSessionId());
        }
        log.info("Session registered: {} (userId={})", session.getSessionId(), userId);
    }

    /**
     * 移除会话。
     *
     * <p>从 {@code sessions} 和 {@code userSessions} 两个索引中同时移除该会话。
     * 若会话所属的用户不再有活跃会话，则同步清理 {@code userSessions} 中的用户条目。</p>
     *
     * @param sessionId 待移除的会话 ID
     * @return 被移除的会话对象，如果会话不存在返回 {@code null}
     */
    public WsSession remove(String sessionId) {
        if (sessionId == null) return null;
        WsSession removed = sessions.remove(sessionId);
        if (removed != null) {
            String userId = removed.getUserId();
            if (userId != null && !userId.isEmpty()) {
                Set<String> ids = userSessions.get(userId);
                if (ids != null) {
                    ids.remove(sessionId);
                    if (ids.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
            }
            removed.disconnect();
            log.info("Session removed: {} (userId={})", sessionId, userId);
        }
        return removed;
    }

    // ======================== 会话查询 ========================

    /**
     * 根据会话 ID 获取会话。
     *
     * @param sessionId 会话 ID
     * @return 对应的 WsSession，不存在返回 {@code null}
     */
    public WsSession getSession(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

    /**
     * 获取指定用户的所有活跃会话。
     *
     * <p>每次调用会实时从 {@code sessions} 中查找，确保返回的会话对象是最新的，
     * 同时自动清理 {@code userSessions} 索引中已失效的会话 ID。</p>
     *
     * @param userId 用户 ID
     * @return 该用户的所有 WsSession 集合，不存在或用户无会话时返回空集合
     */
    public Set<WsSession> getUserSessions(String userId) {
        if (userId == null || userId.isEmpty()) return Collections.emptySet();
        Set<String> ids = userSessions.get(userId);
        if (ids == null || ids.isEmpty()) return Collections.emptySet();
        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有当前在线的会话。
     *
     * @return 所有 WsSession 的集合视图
     */
    public Collection<WsSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * 获取所有在线用户的 ID 集合。
     *
     * @return 在线 userId 集合
     */
    public Set<String> getOnlineUserIds() {
        return new HashSet<>(userSessions.keySet());
    }

    /**
     * 获取当前在线会话总数。
     *
     * @return 会话总数
     */
    public long getSessionCount() {
        return sessions.size();
    }

    /**
     * 获取当前在线用户数。
     *
     * @return 在线用户数
     */
    public long getOnlineUserCount() {
        return userSessions.size();
    }

    // ======================== 会话管理操作 ========================

    /**
     * 强制踢出指定会话（断开连接并从管理器中移除）。
     *
     * <p>被踢出的会话将被关闭底层 WebSocket 连接，并从所有索引中清除。</p>
     *
     * @param sessionId 待踢出的会话 ID
     * @return {@code true} 如果会话存在并被成功踢出，{@code false} 如果会话不存在
     */
    public boolean kickOut(String sessionId) {
        if (sessionId == null) return false;
        WsSession session = sessions.get(sessionId);
        if (session == null) return false;
        try {
            // 先发送踢出通知
            session.sendMessage(WsMessage.of(WsMessage.TYPE_ERROR, "您已被管理员强制下线"));
        } catch (Exception e) {
            log.warn("Failed to send kick-out notification to session {}: {}", sessionId, e.getMessage());
        }
        remove(sessionId);
        log.warn("Session kicked out: {}", sessionId);
        return true;
    }

    /**
     * 判断指定会话是否存在且处于活跃状态。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 如果会话存在且活跃
     */
    public boolean isSessionAlive(String sessionId) {
        WsSession session = getSession(sessionId);
        return session != null && session.isAlive();
    }

    /**
     * 清空所有会话（通常在应用关闭时调用）。
     */
    public void clearAll() {
        log.info("Clearing all sessions, count: {}", sessions.size());
        for (WsSession session : sessions.values()) {
            try {
                session.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting session {}: {}", session.getSessionId(), e.getMessage());
            }
        }
        sessions.clear();
        userSessions.clear();
    }
}
