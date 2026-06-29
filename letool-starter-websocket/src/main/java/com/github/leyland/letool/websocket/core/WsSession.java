package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.websocket.exception.WsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话包装器，封装了 Spring 原生 {@link WebSocketSession}，并扩展了业务属性。
 *
 * <p>该类是 letool WebSocket 模块中的核心会话对象，将框架层面的原生会话与业务层面的
 * 用户信息、心跳时间、扩展属性等关联起来，提供统一的会话操作接口。</p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>生成全局唯一的 {@code sessionId}</li>
 *   <li>关联可选的用户身份（{@code userId}、{@code WsPrincipal}）</li>
 *   <li>记录连接时间和最后心跳时间</li>
 *   <li>通过 {@link WebSocketSession} 发送消息</li>
 *   <li>判断会话是否因心跳超时而脱离活跃状态</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsSession {

    private static final Logger log = LoggerFactory.getLogger(WsSession.class);

    // ======================== 字段 ========================

    /** 全局唯一的会话 ID（UUID） */
    private final String sessionId;

    /** 用户 ID（来自鉴权或匿名） */
    private String userId;

    /** Spring 原生 WebSocket 会话 */
    private final WebSocketSession nativeSession;

    /** 连接建立时间 */
    private final LocalDateTime connectedAt;

    /** 最近一次心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 扩展属性（可存放 WsPrincipal、租户信息等） */
    private final Map<String, Object> attributes;

    /** 是否已断开 */
    private volatile boolean disconnected = false;

    /** 心跳超时阈值（秒），默认 90 秒，由 HeartbeatDetector 在检查时设置 */
    private int heartbeatTimeoutSeconds = 90;

    // ======================== 构造 ========================

    /**
     * 基于原生 WebSocketSession 创建会话包装器。
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     */
    public WsSession(WebSocketSession nativeSession) {
        this.sessionId = UUID.randomUUID().toString().replace("-", "");
        this.nativeSession = Objects.requireNonNull(nativeSession, "nativeSession must not be null");
        this.connectedAt = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
        this.attributes = new ConcurrentHashMap<>();
    }

    /**
     * 基于原生 WebSocketSession 和用户 ID 创建会话包装器。
     *
     * @param nativeSession Spring 原生 WebSocket 会话
     * @param userId        用户唯一标识
     */
    public WsSession(WebSocketSession nativeSession, String userId) {
        this(nativeSession);
        this.userId = userId;
    }

    // ======================== 消息发送 ========================

    /**
     * 向当前会话发送 WebSocket 消息。
     *
     * <p>消息会被序列化为 JSON 字符串并通过原生会话发送。发送前会检查会话是否仍然打开。</p>
     *
     * @param message 待发送的消息对象
     * @throws WsException 发送失败时抛出（例如会话已断开）
     */
    public void sendMessage(WsMessage message) {
        if (disconnected || nativeSession == null || !nativeSession.isOpen()) {
            log.warn("Session {} is closed, cannot send message", sessionId);
            return;
        }
        try {
            String json = JsonUtil.toJsonString(message);
            synchronized (nativeSession) {
                nativeSession.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
            throw new WsException("WS_SEND", "消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 向当前会话发送原始文本消息（不包装为 WsMessage）。
     *
     * @param text 纯文本内容
     */
    public void sendText(String text) {
        if (disconnected || nativeSession == null || !nativeSession.isOpen()) {
            log.warn("Session {} is closed, cannot send text", sessionId);
            return;
        }
        try {
            synchronized (nativeSession) {
                nativeSession.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            log.error("Failed to send text to session {}: {}", sessionId, e.getMessage());
            throw new WsException("WS_SEND", "文本发送失败: " + e.getMessage(), e);
        }
    }

    // ======================== 生命周期 ========================

    /**
     * 判断会话是否仍然活跃（连接未关闭且心跳未超时）。
     *
     * @return {@code true} 如果会话活跃
     */
    public boolean isAlive() {
        if (disconnected) return false;
        if (nativeSession == null || !nativeSession.isOpen()) return false;
        // 心跳超时检查：lastHeartbeat + timeout > now
        return lastHeartbeat.plusSeconds(heartbeatTimeoutSeconds).isAfter(LocalDateTime.now());
    }

    /**
     * 判断原生 WebSocket 会话是否处于打开状态。
     *
     * @return {@code true} 如果原生会话已打开
     */
    public boolean isOpen() {
        return nativeSession != null && nativeSession.isOpen();
    }

    /**
     * 断开当前会话（关闭原生连接并标记为已断开）。
     */
    public void disconnect() {
        this.disconnected = true;
        if (nativeSession != null && nativeSession.isOpen()) {
            try {
                nativeSession.close();
            } catch (IOException e) {
                log.warn("Error closing session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 刷新心跳时间（记录当前时间为最近心跳时间）。
     */
    public void refreshHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }

    // ======================== Getter / Setter ========================

    public String getSessionId() { return sessionId; }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }

    public WebSocketSession getNativeSession() { return nativeSession; }

    public LocalDateTime getConnectedAt() { return connectedAt; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }

    public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) { this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds; }

    public boolean isDisconnected() { return disconnected; }

    /**
     * 获取扩展属性。
     *
     * @param key 属性名
     * @param <T> 属性类型
     * @return 属性值，不存在返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 设置扩展属性。
     *
     * @param key   属性名
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 移除扩展属性。
     *
     * @param key 属性名
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /**
     * 获取全部扩展属性。
     *
     * @return 扩展属性 Map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // ======================== Object 方法 ========================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WsSession session = (WsSession) o;
        return sessionId.equals(session.sessionId);
    }

    @Override
    public int hashCode() {
        return sessionId.hashCode();
    }

    @Override
    public String toString() {
        return "WsSession{sessionId='" + sessionId + "', userId='" + userId + "', open=" + isOpen() + ", alive=" + isAlive() + "}";
    }
}
