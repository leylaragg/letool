package com.github.leyland.letool.websocket.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("WsSessionManager 会话管理器测试")
class WsSessionManagerTest {

    private WsSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new WsSessionManager();
    }

    /**
     * 创建一个 mock WsSession，使用 mock WebSocketSession。
     */
    private WsSession createMockSession(String sessionId, String userId) {
        WebSocketSession mockNative = mock(WebSocketSession.class);
        when(mockNative.isOpen()).thenReturn(true);
        WsSession session = new WsSession(mockNative, userId);
        // 通过反射设置 sessionId 以进行可预测的测试
        try {
            Field f = WsSession.class.getDeclaredField("sessionId");
            f.setAccessible(true);
            f.set(session, sessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return session;
    }

    @Nested
    @DisplayName("register 注册测试")
    class RegisterTests {

        @Test
        @DisplayName("注册会话应加入 sessions 索引")
        void shouldAddToSessionsIndex() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);

            assertSame(session, sessionManager.getSession("s1"));
            assertEquals(1, sessionManager.getSessionCount());
        }

        @Test
        @DisplayName("注册带 userId 的会话应更新 userSessions 索引")
        void shouldUpdateUserSessionsIndex() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);

            Set<WsSession> userSessions = sessionManager.getUserSessions("u1");
            assertEquals(1, userSessions.size());
            assertTrue(userSessions.contains(session));
        }

        @Test
        @DisplayName("注册无 userId 的会话不应加入 userSessions 索引")
        void shouldNotUpdateUserSessionsForAnonymousSession() {
            WsSession session = createMockSession("s1", null);
            sessionManager.register(session);

            assertEquals(1, sessionManager.getSessionCount());
            assertEquals(0, sessionManager.getOnlineUserCount());
        }

        @Test
        @DisplayName("同一用户多次注册应正确累积")
        void multipleSessionsForSameUser() {
            WsSession s1 = createMockSession("s1", "u1");
            WsSession s2 = createMockSession("s2", "u1");
            sessionManager.register(s1);
            sessionManager.register(s2);

            Set<WsSession> userSessions = sessionManager.getUserSessions("u1");
            assertEquals(2, userSessions.size());
            assertEquals(2, sessionManager.getSessionCount());
            assertEquals(1, sessionManager.getOnlineUserCount());
        }

        @Test
        @DisplayName("register(null) 应抛出 NullPointerException")
        void registerNullShouldThrow() {
            assertThrows(NullPointerException.class, () -> sessionManager.register(null));
        }
    }

    @Nested
    @DisplayName("remove 移除测试")
    class RemoveTests {

        @Test
        @DisplayName("移除存在的会话应返回该会话")
        void shouldReturnRemovedSession() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);

            WsSession removed = sessionManager.remove("s1");
            assertSame(session, removed);
        }

        @Test
        @DisplayName("移除后 getSession 应返回 null")
        void shouldNotFindAfterRemove() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);
            sessionManager.remove("s1");

            assertNull(sessionManager.getSession("s1"));
            assertEquals(0, sessionManager.getSessionCount());
        }

        @Test
        @DisplayName("移除用户最后一个会话应清理 userSessions 索引")
        void shouldCleanUserIndexWhenLastSessionRemoved() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);
            sessionManager.remove("s1");

            assertEquals(0, sessionManager.getOnlineUserCount());
            assertTrue(sessionManager.getUserSessions("u1").isEmpty());
        }

        @Test
        @DisplayName("移除不存在的会话应返回 null")
        void removeNonExistentShouldReturnNull() {
            assertNull(sessionManager.remove("ghost"));
        }

        @Test
        @DisplayName("remove(null) 应返回 null")
        void removeNullShouldReturnNull() {
            assertNull(sessionManager.remove(null));
        }

        @Test
        @DisplayName("移除会话应调用 disconnect")
        void removeShouldDisconnectSession() {
            WebSocketSession mockNative = mock(WebSocketSession.class);
            when(mockNative.isOpen()).thenReturn(true);
            WsSession session = new WsSession(mockNative, "u1");
            sessionManager.register(session);

            sessionManager.remove(session.getSessionId());
            assertTrue(session.isDisconnected());
        }
    }

    @Nested
    @DisplayName("查询测试")
    class QueryTests {

        @Test
        @DisplayName("getSession 应正确查找")
        void getSessionShouldFindCorrectly() {
            WsSession s1 = createMockSession("s1", "u1");
            WsSession s2 = createMockSession("s2", "u2");
            sessionManager.register(s1);
            sessionManager.register(s2);

            assertSame(s1, sessionManager.getSession("s1"));
            assertSame(s2, sessionManager.getSession("s2"));
        }

        @Test
        @DisplayName("getSession(null) 应返回 null")
        void getSessionNullShouldReturnNull() {
            assertNull(sessionManager.getSession(null));
        }

        @Test
        @DisplayName("getUserSessions 应返回用户所有会话")
        void getUserSessionsShouldReturnAll() {
            sessionManager.register(createMockSession("s1", "u1"));
            sessionManager.register(createMockSession("s2", "u1"));
            sessionManager.register(createMockSession("s3", "u2"));

            assertEquals(2, sessionManager.getUserSessions("u1").size());
            assertEquals(1, sessionManager.getUserSessions("u2").size());
        }

        @Test
        @DisplayName("getUserSessions(null) 应返回空集合")
        void getUserSessionsNullShouldReturnEmpty() {
            assertTrue(sessionManager.getUserSessions(null).isEmpty());
        }

        @Test
        @DisplayName("getAllSessions 应返回所有会话")
        void getAllSessionsShouldReturnAll() {
            sessionManager.register(createMockSession("s1", "u1"));
            sessionManager.register(createMockSession("s2", "u2"));

            Collection<WsSession> all = sessionManager.getAllSessions();
            assertEquals(2, all.size());
        }

        @Test
        @DisplayName("getOnlineUserIds 应返回所有在线用户 ID")
        void getOnlineUserIdsShouldReturnAllUserIds() {
            sessionManager.register(createMockSession("s1", "u1"));
            sessionManager.register(createMockSession("s2", "u2"));
            sessionManager.register(createMockSession("s3", "u1"));

            Set<String> ids = sessionManager.getOnlineUserIds();
            assertEquals(2, ids.size());
            assertTrue(ids.contains("u1"));
            assertTrue(ids.contains("u2"));
        }

        @Test
        @DisplayName("getSessionCount/getOnlineUserCount 应返回正确计数")
        void countsShouldBeCorrect() {
            assertEquals(0, sessionManager.getSessionCount());
            assertEquals(0, sessionManager.getOnlineUserCount());

            sessionManager.register(createMockSession("s1", "u1"));
            assertEquals(1, sessionManager.getSessionCount());
            assertEquals(1, sessionManager.getOnlineUserCount());

            sessionManager.register(createMockSession("s2", "u1"));
            assertEquals(2, sessionManager.getSessionCount());
            assertEquals(1, sessionManager.getOnlineUserCount());
        }
    }

    @Nested
    @DisplayName("kickOut 踢出测试")
    class KickOutTests {

        @Test
        @DisplayName("踢出存在的会话应返回 true")
        void kickOutExistingShouldReturnTrue() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);

            assertTrue(sessionManager.kickOut("s1"));
            assertNull(sessionManager.getSession("s1"));
        }

        @Test
        @DisplayName("踢出不存在的会话应返回 false")
        void kickOutNonExistentShouldReturnFalse() {
            assertFalse(sessionManager.kickOut("ghost"));
        }

        @Test
        @DisplayName("kickOut(null) 应返回 false")
        void kickOutNullShouldReturnFalse() {
            assertFalse(sessionManager.kickOut(null));
        }
    }

    @Nested
    @DisplayName("isSessionAlive 测试")
    class IsSessionAliveTests {

        @Test
        @DisplayName("存在且活跃的会话应返回 true")
        void aliveSessionShouldReturnTrue() {
            WsSession session = createMockSession("s1", "u1");
            sessionManager.register(session);

            assertTrue(sessionManager.isSessionAlive("s1"));
        }

        @Test
        @DisplayName("不存在的会话应返回 false")
        void nonExistentSessionShouldReturnFalse() {
            assertFalse(sessionManager.isSessionAlive("ghost"));
        }
    }

    @Nested
    @DisplayName("clearAll 清空测试")
    class ClearAllTests {

        @Test
        @DisplayName("clearAll 应清空所有索引")
        void clearAllShouldClearAllIndexes() {
            sessionManager.register(createMockSession("s1", "u1"));
            sessionManager.register(createMockSession("s2", "u2"));

            sessionManager.clearAll();

            assertEquals(0, sessionManager.getSessionCount());
            assertEquals(0, sessionManager.getOnlineUserCount());
            assertTrue(sessionManager.getAllSessions().isEmpty());
        }
    }
}
