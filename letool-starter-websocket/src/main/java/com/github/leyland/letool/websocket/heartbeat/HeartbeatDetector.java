package com.github.leyland.letool.websocket.heartbeat;

import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * 使用 Spring {@link TaskScheduler} 检查连接活动时间的心跳检测器。
 */
public final class HeartbeatDetector {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatDetector.class);

    private final WebSocketProperties properties;
    private final WsSessionManager sessionManager;
    private final WsRoomManager roomManager;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     * 创建心跳检测器。
     *
     * @param properties WebSocket 配置
     * @param sessionManager 会话管理器
     * @param roomManager 房间管理器
     * @param taskScheduler Spring 任务调度器
     */
    public HeartbeatDetector(
            WebSocketProperties properties,
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            TaskScheduler taskScheduler) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler must not be null");
    }

    /**
     * 幂等启动心跳检查任务。
     *
     * @return 实际启动任务时返回 {@code true}
     */
    public synchronized boolean start() {
        if (!properties.getHeartbeat().isEnabled() || isRunning()) {
            return false;
        }
        Duration interval = properties.getHeartbeat().getInterval();
        scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkTimeout, interval);
        return scheduledFuture != null;
    }

    /**
     * 幂等停止心跳检查任务。
     */
    public synchronized void stop() {
        ScheduledFuture<?> future = scheduledFuture;
        scheduledFuture = null;
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 记录指定会话的活动时间。
     *
     * @param sessionId 会话 ID
     */
    public void recordHeartbeat(String sessionId) {
        WsSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.refreshHeartbeat();
        }
    }

    /**
     * 检查并清理全部超时连接。
     *
     * <p>单个连接关闭失败不会中断其他连接的检查。</p>
     */
    public void checkTimeout() {
        for (WsSession session : getInactiveSessions()) {
            try {
                session.disconnect(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (RuntimeException exception) {
                log.debug("关闭心跳超时连接失败，sessionId={}", session.getSessionId(), exception);
            } finally {
                sessionManager.remove(session.getSessionId());
                roomManager.removeSession(session.getSessionId());
            }
        }
    }

    /**
     * 获取当前心跳超时会话快照。
     *
     * @return 超时会话列表
     */
    public List<WsSession> getInactiveSessions() {
        List<WsSession> inactive = new ArrayList<>();
        for (WsSession session : sessionManager.getAllSessions()) {
            if (!session.isAlive()) {
                inactive.add(session);
            }
        }
        return List.copyOf(inactive);
    }

    /**
     * 判断检查任务是否正在运行。
     *
     * @return 任务存在且未取消时返回 {@code true}
     */
    public boolean isRunning() {
        ScheduledFuture<?> future = scheduledFuture;
        return future != null && !future.isCancelled() && !future.isDone();
    }
}
