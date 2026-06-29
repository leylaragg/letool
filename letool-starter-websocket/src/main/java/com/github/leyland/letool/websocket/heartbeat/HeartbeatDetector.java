package com.github.leyland.letool.websocket.heartbeat;

import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 心跳检测器，负责定期检查所有在线会话的心跳状态，及时发现并清理僵尸连接。
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>通过 {@link ScheduledExecutorService} 按配置的间隔时间（{@code heartbeat.interval}）定期执行检查</li>
 *   <li>遍历所有在线会话，检查每个会话的 {@code lastHeartbeat} 时间</li>
 *   <li>如果当前时间减去最后心跳时间超过配置的超时时间（{@code heartbeat.timeout}），
 *       则认为该会话已超时</li>
 *   <li>对超时会话执行断开和注销操作</li>
 * </ol>
 *
 * <p>该检测器是可选组件，可通过 {@code letool.websocket.heartbeat.enabled=false} 禁用。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * // 在应用启动时
 * heartbeatDetector.start();
 *
 * // 在应用关闭时
 * heartbeatDetector.stop();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class HeartbeatDetector {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatDetector.class);

    // ======================== 字段 ========================

    /** WebSocket 配置属性 */
    private final WebSocketProperties properties;

    /** 会话管理器，用于查询和注销会话 */
    private final WsSessionManager sessionManager;

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduler;

    /** 定时任务句柄，用于取消任务 */
    private ScheduledFuture<?> scheduledFuture;

    /** 是否正在运行 */
    private volatile boolean running = false;

    // ======================== 构造 ========================

    /**
     * 创建心跳检测器。
     *
     * @param properties     WebSocket 配置属性
     * @param sessionManager 会话管理器
     */
    public HeartbeatDetector(WebSocketProperties properties, WsSessionManager sessionManager) {
        this.properties = properties;
        this.sessionManager = sessionManager;
    }

    // ======================== 生命周期 ========================

    /**
     * 启动心跳检测定时任务。
     *
     * <p>只有当 {@code letool.websocket.heartbeat.enabled=true} 时才会真正启动。
     * 任务以固定频率执行，间隔时间由 {@code heartbeat.interval} 配置（默认 30 秒）。</p>
     *
     * @return {@code true} 如果成功启动，{@code false} 如果心跳检测被禁用或已在运行
     */
    public synchronized boolean start() {
        if (!properties.getHeartbeat().isEnabled()) {
            log.info("Heartbeat detection is disabled");
            return false;
        }
        if (running) {
            log.warn("HeartbeatDetector is already running");
            return false;
        }
        int interval = properties.getHeartbeat().getInterval();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-heartbeat-detector");
            t.setDaemon(true);
            return t;
        });
        scheduledFuture = scheduler.scheduleAtFixedRate(
                this::checkTimeout, interval, interval, TimeUnit.SECONDS);
        running = true;
        log.info("HeartbeatDetector started, interval={}s, timeout={}s",
                properties.getHeartbeat().getInterval(), properties.getHeartbeat().getTimeout());
        return true;
    }

    /**
     * 停止心跳检测定时任务。
     *
     * <p>取消定时任务并关闭线程池。任务取消时会等待当前正在执行的检查完成
     * （最多等待 5 秒），然后强制中断。</p>
     */
    public synchronized void stop() {
        if (!running) return;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        running = false;
        log.info("HeartbeatDetector stopped");
    }

    // ======================== 心跳记录 ========================

    /**
     * 记录指定会话的心跳时间（刷新为当前时间）。
     *
     * @param sessionId 会话 ID
     */
    public void recordHeartbeat(String sessionId) {
        WsSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.refreshHeartbeat();
        }
    }

    // ======================== 超时检查 ========================

    /**
     * 执行一轮心跳超时检查。
     *
     * <p>遍历所有在线会话，将最后心跳时间超过配置超时阈值的会话标记为超时并断开。
     * 对于每个超时会话，执行以下操作：</p>
     * <ol>
     *   <li>发送超时通知（如果会话仍可写入）</li>
     *   <li>从 SessionManager 中移除会话</li>
     * </ol>
     */
    public void checkTimeout() {
        try {
            List<WsSession> inactive = getInactiveSessions();
            if (inactive.isEmpty()) return;

            for (WsSession session : inactive) {
                log.info("Session {} (userId={}) heartbeat timeout, disconnecting",
                        session.getSessionId(), session.getUserId());
                try {
                    session.sendMessage(WsMessage.error("心跳超时，连接已断开"));
                } catch (Exception ignored) {
                    // 忽略：会话可能已不可写
                }
                sessionManager.remove(session.getSessionId());
            }

            log.debug("Heartbeat check: total={}, timeout={}",
                    sessionManager.getSessionCount(), inactive.size());
        } catch (Exception e) {
            log.error("Error during heartbeat check: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取当前所有心跳超时的不活跃会话列表。
     *
     * <p>心跳超时判断：{@code lastHeartbeat + timeout < 当前时间}</p>
     *
     * @return 不活跃会话列表，无线程安全问题（每次返回新列表）
     */
    public List<WsSession> getInactiveSessions() {
        int timeout = properties.getHeartbeat().getTimeout();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeout);
        List<WsSession> inactive = new ArrayList<>();
        for (WsSession session : sessionManager.getAllSessions()) {
            if (session.getLastHeartbeat() != null && session.getLastHeartbeat().isBefore(threshold)) {
                inactive.add(session);
            }
        }
        return inactive;
    }

    /**
     * 获取心跳检测器是否正在运行。
     *
     * @return {@code true} 如果正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
