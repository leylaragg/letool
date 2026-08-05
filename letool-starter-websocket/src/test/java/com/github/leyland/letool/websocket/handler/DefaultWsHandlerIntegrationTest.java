package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.tool.json.Fastjson2JsonCodec;
import com.github.leyland.letool.websocket.auth.WsHandshakeInterceptor;
import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsMessageCodec;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.util.unit.DataSize;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultWsHandler} 的协议安全与连接清理集成测试。
 */
class DefaultWsHandlerIntegrationTest {

    /**
     * 验证超大消息使用标准状态关闭连接并清理索引。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldCloseOversizedMessageAndCleanSession() throws Exception {
        Fixture fixture = fixture(4, new DefaultWsMessageRouter());
        WebSocketSession nativeSession = openNativeSession("session-1", authenticatedPrincipal("user-1"));
        fixture.handler.afterConnectionEstablished(nativeSession);

        fixture.handler.handleTextMessage(nativeSession, new TextMessage("中文"));

        verify(nativeSession).close(CloseStatus.TOO_BIG_TO_PROCESS);
        assertThat(fixture.sessionManager.getSession("session-1")).isNull();
    }

    /**
     * 验证处理器异常只向客户端发送稳定错误码，不泄露底层异常消息。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldSendSanitizedErrorFrameWhenBusinessHandlerFails() throws Exception {
        DefaultWsMessageRouter router = new DefaultWsMessageRouter();
        router.register("explode", new WsMessageHandler() {
            @Override
            public void handle(WsSession session, WsMessage message) {
                throw new IllegalStateException("database-password");
            }

            @Override
            public String getMessageType() {
                return "explode";
            }
        });
        Fixture fixture = fixture(1024, router);
        WebSocketSession nativeSession = openNativeSession("session-2", authenticatedPrincipal("user-2"));
        fixture.handler.afterConnectionEstablished(nativeSession);

        fixture.handler.handleTextMessage(nativeSession,
                new TextMessage(fixture.codec.encode(WsMessage.of("explode", "payload"))));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(nativeSession, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TextMessage::getPayload)
                .anySatisfy(payload -> {
                    assertThat(payload).contains("WS_008");
                    assertThat(payload).doesNotContain("database-password");
                });
        assertThat(fixture.sessionManager.getSession("session-2")).isNotNull();
    }

    /**
     * 验证原生 Pong 帧会刷新会话活动时间。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldRefreshActivityWhenPongFrameArrives() throws Exception {
        Fixture fixture = fixture(1024, new DefaultWsMessageRouter());
        WebSocketSession nativeSession = openNativeSession("session-3", authenticatedPrincipal("user-3"));
        fixture.handler.afterConnectionEstablished(nativeSession);
        WsSession session = fixture.sessionManager.getSession("session-3");
        session.setLastHeartbeat(1L);

        fixture.handler.handlePongMessage(nativeSession, new PongMessage(ByteBuffer.allocate(0)));

        assertThat(session.getLastHeartbeat()).isGreaterThan(1L);
    }

    /**
     * 验证正常关闭同步清理会话与房间反向索引。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldCleanSessionAndRoomsWhenConnectionCloses() throws Exception {
        Fixture fixture = fixture(1024, new DefaultWsMessageRouter());
        WebSocketSession nativeSession = openNativeSession("session-4", authenticatedPrincipal("user-4"));
        fixture.handler.afterConnectionEstablished(nativeSession);
        WsSession session = fixture.sessionManager.getSession("session-4");
        fixture.roomManager.create("room-4", "房间四");
        fixture.roomManager.join("room-4", session);

        fixture.handler.afterConnectionClosed(nativeSession, CloseStatus.NORMAL);

        assertThat(fixture.sessionManager.getSession("session-4")).isNull();
        assertThat(fixture.roomManager.getRoom("room-4")).isNull();
    }

    /**
     * 创建处理器测试夹具。
     *
     * @param maxFrameSize 最大消息大小
     * @param router 消息路由器
     * @return 测试夹具
     */
    private Fixture fixture(int maxFrameSize, WsMessageRouter router) {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setMaxFrameSize(DataSize.ofBytes(maxFrameSize));
        WsMessageCodec codec = new WsMessageCodec(Fastjson2JsonCodec.createDefault());
        WsSessionManager sessionManager = new WsSessionManager(3);
        WsRoomManager roomManager = new WsRoomManager(sessionManager, codec);
        WsErrorHandler errorHandler = new DefaultWsErrorHandler(codec);
        DefaultWsHandler handler = new DefaultWsHandler(
                sessionManager, roomManager, null, router, codec, errorHandler, properties);
        return new Fixture(handler, sessionManager, roomManager, codec);
    }

    /**
     * 创建已认证主体属性。
     *
     * @param userId 用户标识
     * @return 原生会话属性
     */
    private Map<String, Object> authenticatedPrincipal(String userId) {
        return Map.of(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, new WsPrincipal(userId));
    }

    /**
     * 创建打开状态的原生会话。
     *
     * @param sessionId 会话 ID
     * @param attributes 握手属性
     * @return 原生会话
     */
    private WebSocketSession openNativeSession(String sessionId, Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    /**
     * 处理器测试夹具。
     *
     * @param handler 默认处理器
     * @param sessionManager 会话管理器
     * @param roomManager 房间管理器
     * @param codec 消息编解码器
     */
    private record Fixture(
            DefaultWsHandler handler,
            WsSessionManager sessionManager,
            WsRoomManager roomManager,
            WsMessageCodec codec) {
    }
}
