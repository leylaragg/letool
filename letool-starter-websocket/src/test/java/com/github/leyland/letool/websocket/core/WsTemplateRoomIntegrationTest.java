package com.github.leyland.letool.websocket.core;

import com.github.leyland.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local room broadcast integration tests for {@link WsTemplate}.
 */
class WsTemplateRoomIntegrationTest {

    /**
     * Verifies room sends are scoped to room members and honor sender exclusion.
     */
    @Test
    void shouldSendRoomMessageOnlyToRoomMembers() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        WsTemplate template = new WsTemplate(sessionManager, roomManager);
        WebSocketSession nativeOne = openNativeSession();
        WebSocketSession nativeTwo = openNativeSession();
        WebSocketSession nativeOutside = openNativeSession();
        WsSession sessionOne = new WsSession(nativeOne, "u1");
        WsSession sessionTwo = new WsSession(nativeTwo, "u2");
        WsSession outsideSession = new WsSession(nativeOutside, "u3");
        sessionManager.register(sessionOne);
        sessionManager.register(sessionTwo);
        sessionManager.register(outsideSession);
        roomManager.create("room-1", "Room 1");
        roomManager.join("room-1", sessionOne);
        roomManager.join("room-1", sessionTwo);

        template.sendToRoom("room-1", WsMessage.text("hello"), sessionOne.getSessionId());

        verify(nativeOne, never()).sendMessage(any(TextMessage.class));
        verify(nativeTwo).sendMessage(any(TextMessage.class));
        verify(nativeOutside, never()).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession openNativeSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
