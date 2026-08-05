package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.websocket.exception.WsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用双索引维护本进程 WebSocket 会话的线程安全管理器。
 *
 * <p>同一用户的注册、限流与删除由稳定分段锁串行化，避免主索引和用户索引在
 * 并发连接/断开时产生永久不一致。</p>
 */
public class WsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WsSessionManager.class);
    private static final int LOCK_STRIPE_COUNT = 128;

    private final ConcurrentHashMap<String, WsSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Object[] userLocks = createLocks();
    private final int maxSessionPerUser;

    /**
     * 使用默认单用户上限创建管理器。
     */
    public WsSessionManager() {
        this(5);
    }

    /**
     * 创建指定单用户会话上限的管理器。
     *
     * @param maxSessionPerUser 单用户最大同时在线会话数
     */
    public WsSessionManager(int maxSessionPerUser) {
        if (maxSessionPerUser <= 0) {
            throw new IllegalArgumentException("maxSessionPerUser must be positive");
        }
        this.maxSessionPerUser = maxSessionPerUser;
    }

    /**
     * 原子校验用户限额并注册会话双索引。
     *
     * @param session 待注册会话
     * @throws WsException 单用户会话达到上限或会话 ID 冲突时抛出
     */
    public void register(WsSession session) {
        Objects.requireNonNull(session, "session must not be null");
        synchronized (session) {
            String userId = session.getUserId();
            if (userId == null || userId.isBlank()) {
                registerWithoutUser(session);
                return;
            }
            synchronized (lockFor(userId)) {
                WsSession existing = sessions.get(session.getSessionId());
                if (existing == session) {
                    return;
                }
                if (existing != null) {
                    throw WsException.configurationInvalid("WebSocket 会话 ID 冲突");
                }
                Set<String> currentIds = userSessions.get(userId);
                int currentSize = currentIds == null ? 0 : currentIds.size();
                if (currentSize >= maxSessionPerUser) {
                    throw WsException.sessionLimitExceeded(userId);
                }

                // 限流通过后才暴露会话，并在同一用户临界区内完成反向索引。
                WsSession raced = sessions.putIfAbsent(session.getSessionId(), session);
                if (raced != null && raced != session) {
                    throw WsException.configurationInvalid("WebSocket 会话 ID 冲突");
                }
                userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(session.getSessionId());
            }
            log.debug("WebSocket 会话已注册，sessionId={}，userId={}", session.getSessionId(), userId);
        }
    }

    /**
     * 幂等移除会话及其用户索引并关闭连接。
     *
     * @param sessionId 会话 ID
     * @return 被移除的会话，不存在时返回 {@code null}
     */
    public WsSession remove(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        WsSession candidate = sessions.get(sessionId);
        if (candidate == null) {
            return null;
        }
        synchronized (candidate) {
            String userId = candidate.getUserId();
            if (userId == null || userId.isBlank()) {
                return sessions.remove(sessionId, candidate) ? disconnect(candidate) : null;
            }
            synchronized (lockFor(userId)) {
                if (!sessions.remove(sessionId, candidate)) {
                    return null;
                }
                userSessions.computeIfPresent(userId, (ignored, ids) -> {
                    ids.remove(sessionId);
                    return ids.isEmpty() ? null : ids;
                });
            }
            return disconnect(candidate);
        }
    }

    /**
     * 按 ID 查询会话。
     *
     * @param sessionId 会话 ID
     * @return 会话，不存在时返回 {@code null}
     */
    public WsSession getSession(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    /**
     * 查询指定用户的会话快照。
     *
     * @param userId 用户标识
     * @return 不可变会话快照
     */
    public Set<WsSession> getUserSessions(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        synchronized (lockFor(userId)) {
            Set<String> ids = userSessions.get(userId);
            if (ids == null || ids.isEmpty()) {
                return Set.of();
            }
            Set<WsSession> result = new LinkedHashSet<>();
            for (String sessionId : ids) {
                WsSession session = sessions.get(sessionId);
                if (session != null) {
                    result.add(session);
                }
            }
            return Set.copyOf(result);
        }
    }

    /**
     * 获取全部会话快照。
     *
     * @return 不可变会话快照
     */
    public Collection<WsSession> getAllSessions() {
        return java.util.List.copyOf(sessions.values());
    }

    /**
     * 获取在线用户标识快照。
     *
     * @return 不可变用户标识集合
     */
    public Set<String> getOnlineUserIds() {
        return Set.copyOf(userSessions.keySet());
    }

    /**
     * 获取在线会话数。
     *
     * @return 在线会话数
     */
    public long getSessionCount() {
        return sessions.size();
    }

    /**
     * 获取在线用户数。
     *
     * @return 在线用户数
     */
    public long getOnlineUserCount() {
        return userSessions.size();
    }

    /**
     * 强制断开指定会话。
     *
     * @param sessionId 会话 ID
     * @return 会话存在时返回 {@code true}
     */
    public boolean kickOut(String sessionId) {
        return remove(sessionId) != null;
    }

    /**
     * 判断会话是否处于活动状态。
     *
     * @param sessionId 会话 ID
     * @return 会话活动时返回 {@code true}
     */
    public boolean isSessionAlive(String sessionId) {
        WsSession session = getSession(sessionId);
        return session != null && session.isAlive();
    }

    /**
     * 幂等关闭并清空全部会话。
     */
    public void clearAll() {
        for (String sessionId : Set.copyOf(sessions.keySet())) {
            remove(sessionId);
        }
    }

    /**
     * 注册没有业务用户标识的独立会话。
     *
     * @param session 待注册会话
     */
    private void registerWithoutUser(WsSession session) {
        WsSession existing = sessions.putIfAbsent(session.getSessionId(), session);
        if (existing != null && existing != session) {
            throw WsException.configurationInvalid("WebSocket 会话 ID 冲突");
        }
    }

    /**
     * 关闭被移除的会话。
     *
     * @param session 被移除会话
     * @return 原会话，不存在时返回 {@code null}
     */
    private WsSession disconnect(WsSession session) {
        if (session != null) {
            session.disconnect();
        }
        return session;
    }

    /**
     * 获取用户对应的稳定分段锁。
     *
     * @param userId 用户标识
     * @return 分段锁
     */
    private Object lockFor(String userId) {
        return userLocks[(userId.hashCode() & Integer.MAX_VALUE) % userLocks.length];
    }

    /**
     * 创建固定数量的分段锁，避免按用户无限增长锁对象。
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
