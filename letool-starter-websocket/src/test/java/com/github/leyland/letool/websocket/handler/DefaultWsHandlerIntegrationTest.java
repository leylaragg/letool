package com.github.leyland.letool.websocket.handler;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsSessionManager;
import com.github.leyland.letool.websocket.room.WsRoom;
import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runtime lifecycle tests for {@link DefaultWsHandler}.
 */
class DefaultWsHandlerIntegrationTest {

    /**
     * Verifies connection establishment registers a principal-bound session and sends a welcome message.
     */
    @Test
    void shouldRegisterSessionWithPrincipalOnConnection() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        DefaultWsHandler handler = new DefaultWsHandler(sessionManager, roomManager, null, List.of());
        WebSocketSession nativeSession = openNativeSession("native-1",
                Map.of("principal", new WsPrincipal("user-1", "User One", List.of("admin"))));

        handler.afterConnectionEstablished(nativeSession);

        WsSession registered = sessionManager.getAllSessions().iterator().next();
        assertThat(registered.getUserId()).isEqualTo("user-1");
        assertThat(registered.<WsPrincipal>getAttribute("principal").getUsername()).isEqualTo("User One");
        assertSentMessage(nativeSession, WsMessage.TYPE_NOTIFICATION);
    }

    /**
     * Verifies incoming ping messages are answered with pong by the same handler path.
     */
    @Test
    void shouldReplyPongWhenPingMessageArrives() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        DefaultWsHandler handler = new DefaultWsHandler(sessionManager, roomManager, null, List.of());
        WebSocketSession nativeSession = openNativeSession("native-2", Map.of());
        handler.afterConnectionEstablished(nativeSession);

        handler.handleTextMessage(nativeSession, new TextMessage(JsonUtil.toJsonString(WsMessage.of(WsMessage.TYPE_PING, ""))));

        assertSentMessage(nativeSession, WsMessage.TYPE_PONG);
    }

    /**
     * Verifies business messages are routed to the matching {@link WsMessageHandler}.
     */
    @Test
    void shouldDispatchBusinessMessageToRegisteredHandler() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        AtomicReference<WsSession> handledSession = new AtomicReference<>();
        AtomicReference<WsMessage> handledMessage = new AtomicReference<>();
        WsMessageHandler messageHandler = new WsMessageHandler() {
            @Override
            public void handle(WsSession session, WsMessage message) {
                handledSession.set(session);
                handledMessage.set(message);
            }

            @Override
            public String getMessageType() {
                return "chat";
            }
        };
        DefaultWsHandler handler = new DefaultWsHandler(sessionManager, roomManager, null, List.of(messageHandler));
        WebSocketSession nativeSession = openNativeSession("native-3",
                Map.of("principal", new WsPrincipal("user-3")));
        handler.afterConnectionEstablished(nativeSession);

        handler.handleTextMessage(nativeSession, new TextMessage(JsonUtil.toJsonString(WsMessage.of("chat", "hello"))));

        assertThat(handledSession.get()).isNotNull();
        assertThat(handledSession.get().getUserId()).isEqualTo("user-3");
        assertThat(handledMessage.get().getType()).isEqualTo("chat");
        assertThat(handledMessage.get().getSenderId()).isEqualTo("user-3");
    }

    /**
     * Verifies closing a connection removes the session from joined rooms and the session registry.
     */
    @Test
    void shouldCleanSessionAndRoomsWhenConnectionCloses() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        DefaultWsHandler handler = new DefaultWsHandler(sessionManager, roomManager, null, List.of());
        WebSocketSession nativeSession = openNativeSession("native-4",
                Map.of("principal", new WsPrincipal("user-4")));
        handler.afterConnectionEstablished(nativeSession);
        WsSession session = sessionManager.getAllSessions().iterator().next();
        roomManager.create("room-4", "Room 4");
        roomManager.join("room-4", session);

        handler.afterConnectionClosed(nativeSession, CloseStatus.NORMAL);

        assertThat(sessionManager.getAllSessions()).isEmpty();
        assertThat(roomManager.getAllRooms()).extracting(WsRoom::getRoomId).doesNotContain("room-4");
    }

    private WebSocketSession openNativeSession(String sessionId, Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        return session;
    }

    private void assertSentMessage(WebSocketSession nativeSession, String expectedType) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(nativeSession, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TextMessage::getPayload)
                .map(payload -> JsonUtil.parseObject(payload, WsMessage.class).getType())
                .contains(expectedType);
    }
}
