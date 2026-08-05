package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.websocket.exception.WsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带有并发发送保护和业务身份的 WebSocket 会话。
 *
 * <p>会话 ID 直接使用容器提供的原生 ID。发送由 Spring
 * {@link ConcurrentWebSocketSessionDecorator} 串行化，并设置发送时间和缓冲区上限，
 * 防止慢连接无限占用内存。</p>
 */
public final class WsSession {

    private static final Logger log = LoggerFactory.getLogger(WsSession.class);
    private static final int DEFAULT_SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int DEFAULT_SEND_BUFFER_SIZE_BYTES = 512 * 1024;

    private final String sessionId;
    private final String userId;
    private final WebSocketSession nativeSession;
    private final long connectedAt;
    private final AtomicLong lastActivityAt;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private volatile long heartbeatTimeoutMillis = Duration.ofSeconds(90).toMillis();
    private volatile boolean heartbeatCheckEnabled = true;

    /**
     * 使用默认发送限制创建会话。
     *
     * @param nativeSession Spring 原生会话
     */
    public WsSession(WebSocketSession nativeSession) {
        this(nativeSession, null);
    }

    /**
     * 使用默认发送限制创建已绑定用户的会话。
     *
     * @param nativeSession Spring 原生会话
     * @param userId 用户唯一标识
     */
    public WsSession(WebSocketSession nativeSession, String userId) {
        this(nativeSession, userId, DEFAULT_SEND_TIME_LIMIT_MILLIS, DEFAULT_SEND_BUFFER_SIZE_BYTES);
    }

    /**
     * 创建带指定发送限制的会话。
     *
     * @param nativeSession Spring 原生会话
     * @param userId 用户唯一标识
     * @param sendTimeLimitMillis 单次发送时间上限，单位毫秒
     * @param sendBufferSizeBytes 待发送缓冲区上限，单位字节
     */
    public WsSession(
            WebSocketSession nativeSession,
            String userId,
            int sendTimeLimitMillis,
            int sendBufferSizeBytes) {
        WebSocketSession requiredSession = Objects.requireNonNull(nativeSession, "nativeSession must not be null");
        if (requiredSession.getId() == null || requiredSession.getId().isBlank()) {
            throw new IllegalArgumentException("nativeSession id must not be blank");
        }
        if (sendTimeLimitMillis <= 0 || sendBufferSizeBytes <= 0) {
            throw new IllegalArgumentException("send limits must be positive");
        }
        this.sessionId = requiredSession.getId();
        this.userId = userId;
        this.nativeSession = new ConcurrentWebSocketSessionDecorator(
                requiredSession,
                sendTimeLimitMillis,
                sendBufferSizeBytes,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        this.connectedAt = System.currentTimeMillis();
        this.lastActivityAt = new AtomicLong(connectedAt);
    }

    /**
     * 发送原始文本帧。
     *
     * @param text 待发送文本
     * @throws WsException 会话关闭或发送失败时抛出
     */
    public void sendText(String text) {
        if (!isOpen()) {
            throw WsException.deliveryFailed(sessionId,
                    new IllegalStateException("WebSocket session is closed"));
        }
        try {
            nativeSession.sendMessage(new TextMessage(Objects.requireNonNull(text, "text must not be null")));
        } catch (IOException | RuntimeException exception) {
            throw WsException.deliveryFailed(sessionId, exception);
        }
    }

    /**
     * 判断会话是否仍处于活动状态。
     *
     * @return 连接打开且未超过心跳阈值时返回 {@code true}
     */
    public boolean isAlive() {
        return isOpen()
                && (!heartbeatCheckEnabled
                || System.currentTimeMillis() - lastActivityAt.get() < heartbeatTimeoutMillis);
    }

    /**
     * 判断底层连接是否打开。
     *
     * @return 底层连接打开时返回 {@code true}
     */
    public boolean isOpen() {
        return !disconnected.get() && nativeSession.isOpen();
    }

    /**
     * 使用正常状态关闭连接。
     */
    public void disconnect() {
        disconnect(CloseStatus.NORMAL);
    }

    /**
     * 使用指定状态幂等关闭连接。
     *
     * @param closeStatus WebSocket 关闭状态
     */
    public void disconnect(CloseStatus closeStatus) {
        if (!disconnected.compareAndSet(false, true)) {
            return;
        }
        if (nativeSession.isOpen()) {
            try {
                nativeSession.close(closeStatus);
            } catch (IOException exception) {
                log.debug("关闭 WebSocket 会话失败，sessionId={}", sessionId, exception);
            }
        }
    }

    /**
     * 刷新最近活动时间。
     */
    public void refreshHeartbeat() {
        lastActivityAt.set(System.currentTimeMillis());
    }

    /**
     * 获取会话 ID。
     *
     * @return 原生会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取用户标识。
     *
     * @return 用户标识，尚未绑定时为 {@code null}
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取经过并发保护的原生会话。
     *
     * @return Spring WebSocket 会话
     */
    public WebSocketSession getNativeSession() {
        return nativeSession;
    }

    /**
     * 获取连接建立时间。
     *
     * @return Epoch 毫秒
     */
    public long getConnectedAt() {
        return connectedAt;
    }

    /**
     * 获取最近活动时间。
     *
     * @return Epoch 毫秒
     */
    public long getLastHeartbeat() {
        return lastActivityAt.get();
    }

    /**
     * 设置最近活动时间，主要用于可控时钟的检测场景。
     *
     * @param lastHeartbeat Epoch 毫秒
     */
    public void setLastHeartbeat(long lastHeartbeat) {
        lastActivityAt.set(lastHeartbeat);
    }

    /**
     * 获取心跳超时阈值。
     *
     * @return 心跳超时时间
     */
    public Duration getHeartbeatTimeout() {
        return Duration.ofMillis(heartbeatTimeoutMillis);
    }

    /**
     * 设置心跳超时阈值。
     *
     * @param heartbeatTimeout 心跳超时时间
     */
    public void setHeartbeatTimeout(Duration heartbeatTimeout) {
        if (heartbeatTimeout == null || heartbeatTimeout.toMillis() <= 0) {
            throw new IllegalArgumentException("heartbeatTimeout must be positive");
        }
        this.heartbeatTimeoutMillis = heartbeatTimeout.toMillis();
    }

    /**
     * 设置是否在活动状态判断中应用心跳超时。
     *
     * @param heartbeatCheckEnabled 是否启用心跳超时判断
     */
    public void setHeartbeatCheckEnabled(boolean heartbeatCheckEnabled) {
        this.heartbeatCheckEnabled = heartbeatCheckEnabled;
    }

    /**
     * 判断会话是否已经执行断开流程。
     *
     * @return 已断开时返回 {@code true}
     */
    public boolean isDisconnected() {
        return disconnected.get();
    }

    /**
     * 获取业务扩展属性。
     *
     * @param key 属性名称
     * @param <T> 属性类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 设置业务扩展属性。
     *
     * @param key 属性名称
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(Objects.requireNonNull(key, "key must not be null"),
                Objects.requireNonNull(value, "value must not be null"));
    }

    /**
     * 删除业务扩展属性。
     *
     * @param key 属性名称
     */
    public void removeAttribute(String key) {
        if (key != null) {
            attributes.remove(key);
        }
    }

    /**
     * 获取不可变属性快照。
     *
     * @return 不可变属性快照
     */
    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }
}
