package io.github.leylaragg.letool.websocket.core;

import io.github.leylaragg.letool.websocket.room.WsRoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WsTemplate} 房间投递的关键集成契约测试。
 */
class WsTemplateRoomIntegrationTest {

    /**
     * 验证房间广播隔离单连接失败并返回可观测的投递结果。
     *
     * @throws Exception 模拟原生发送失败时抛出
     */
    @Test
    void shouldIsolateRoomDeliveryFailureAndReportOutcome() throws Exception {
        WsSessionManager sessionManager = new WsSessionManager(3);
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        WsTemplate template = new WsTemplate(sessionManager, roomManager);
        WebSocketSession successfulNative = openNativeSession("session-1");
        WebSocketSession failedNative = openNativeSession("session-2");
        WebSocketSession outsideNative = openNativeSession("session-3");
        doThrow(new IOException("connection reset"))
                .when(failedNative).sendMessage(any(TextMessage.class));
        WsSession successful = new WsSession(successfulNative, "user-1");
        WsSession failed = new WsSession(failedNative, "user-2");
        WsSession outside = new WsSession(outsideNative, "user-3");
        sessionManager.register(successful);
        sessionManager.register(failed);
        sessionManager.register(outside);
        roomManager.create("room-1", "房间一");
        roomManager.join("room-1", successful);
        roomManager.join("room-1", failed);

        WsDeliveryResult result = template.sendToRoom("room-1", WsMessage.text("hello"));

        assertThat(result.getTargetCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getStaleSessionCount()).isZero();
        verify(successfulNative).sendMessage(any(TextMessage.class));
        verify(outsideNative, never()).sendMessage(any(TextMessage.class));
    }

    /**
     * 验证连接清理会同步删除全部房间反向索引。
     */
    @Test
    void shouldRemoveSessionFromEveryRoomWithoutMutatingSnapshots() {
        WsSessionManager sessionManager = new WsSessionManager(2);
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        WsSession session = new WsSession(openNativeSession("session-1"), "user-1");
        sessionManager.register(session);
        roomManager.create("room-1", "房间一");
        roomManager.create("room-2", "房间二");
        roomManager.join("room-1", session);
        roomManager.join("room-2", session);

        roomManager.removeSession(session.getSessionId());

        assertThat(roomManager.getSessionRooms(session.getSessionId())).isEmpty();
        assertThat(roomManager.getRoom("room-1")).isNull();
        assertThat(roomManager.getRoom("room-2")).isNull();
    }

    /**
     * 验证连接加入与清理并发执行后，房间主索引和反向索引保持一致。
     *
     * @throws Exception 并发任务执行失败时抛出
     */
    @Test
    void shouldKeepRoomIndexesConsistentWhenSessionIsRemovedDuringJoin() throws Exception {
        PausingSessionManager sessionManager = new PausingSessionManager();
        WsRoomManager roomManager = new WsRoomManager(sessionManager);
        WsSession session = new WsSession(openNativeSession("session-race"), "user-race");
        sessionManager.register(session);
        roomManager.create("room-race", "并发房间");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> joined = executor.submit(() -> roomManager.join("room-race", session));
            assertThat(sessionManager.awaitJoinLookup()).isTrue();
            CountDownLatch cleanupStarted = new CountDownLatch(1);
            Future<?> cleanup = executor.submit(() -> {
                cleanupStarted.countDown();
                sessionManager.remove(session.getSessionId());
                roomManager.removeSession(session.getSessionId());
            });
            assertThat(cleanupStarted.await(3, TimeUnit.SECONDS)).isTrue();

            sessionManager.continueLookup();

            assertThat(joined.get(3, TimeUnit.SECONDS)).isTrue();
            cleanup.get(3, TimeUnit.SECONDS);
            assertThat(roomManager.getRoom("room-race")).isNull();
            assertThat(roomManager.getSessionRooms(session.getSessionId())).isEmpty();
        } finally {
            sessionManager.continueLookup();
            executor.shutdownNow();
        }
    }

    /**
     * 创建打开状态的原生测试会话。
     *
     * @param sessionId 原生会话 ID
     * @return 原生测试会话
     */
    private WebSocketSession openNativeSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    /**
     * 在加入房间的会话校验处暂停，稳定复现连接清理竞态。
     */
    private static final class PausingSessionManager extends WsSessionManager {

        private final AtomicInteger lookupCount = new AtomicInteger();
        private final CountDownLatch joinLookupReached = new CountDownLatch(1);
        private final CountDownLatch continueLookup = new CountDownLatch(1);

        /**
         * 在加入流程查询会话时暂停，让清理线程进入竞争窗口。
         *
         * @param sessionId 会话 ID
         * @return 当前会话，移除后为 {@code null}
         */
        @Override
        public WsSession getSession(String sessionId) {
            if (lookupCount.incrementAndGet() == 1) {
                joinLookupReached.countDown();
                try {
                    if (!continueLookup.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待并发会话清理超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待并发会话清理被中断", exception);
                }
            }
            return super.getSession(sessionId);
        }

        /**
         * 等待加入流程到达第二次会话校验。
         *
         * @return 在超时前到达时返回 {@code true}
         * @throws InterruptedException 等待被中断时抛出
         */
        private boolean awaitJoinLookup() throws InterruptedException {
            return joinLookupReached.await(3, TimeUnit.SECONDS);
        }

        /**
         * 允许被暂停的会话查询继续执行。
         */
        private void continueLookup() {
            continueLookup.countDown();
        }
    }
}
