package com.github.leyland.letool.websocket.heartbeat;

import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HeartbeatDetector} 的超时资源清理测试。
 */
class HeartbeatDetectorTest {

    /**
     * 验证心跳超时会关闭连接并同步清理会话和房间索引。
     */
    @Test
    void shouldCloseTimedOutSessionAndCleanAllIndexes() throws Exception {
        WebSocketProperties properties = new WebSocketProperties();
        properties.getHeartbeat().setTimeout(Duration.ofSeconds(1));
        WsSessionManager sessionManager = new WsSessionManager(2);
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        HeartbeatDetector detector = new HeartbeatDetector(
                properties, sessionManager, roomManager, mock(TaskScheduler.class));
        WebSocketSession nativeSession = mock(WebSocketSession.class);
        when(nativeSession.getId()).thenReturn("session-1");
        when(nativeSession.isOpen()).thenReturn(true);
        WsSession session = new WsSession(nativeSession, "user-1");
        session.setHeartbeatTimeout(Duration.ofSeconds(1));
        sessionManager.register(session);
        roomManager.create("room-1", "房间一");
        roomManager.join("room-1", session);
        session.setLastHeartbeat(System.currentTimeMillis() - 2_000L);

        detector.checkTimeout();

        verify(nativeSession).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertThat(sessionManager.getSession("session-1")).isNull();
        assertThat(roomManager.getRoom("room-1")).isNull();
    }
}
