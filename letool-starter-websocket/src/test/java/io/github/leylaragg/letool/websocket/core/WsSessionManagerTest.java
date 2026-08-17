package io.github.leylaragg.letool.websocket.core;

import io.github.leylaragg.letool.websocket.exception.WsException;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WsSessionManager} 的并发索引契约测试。
 */
class WsSessionManagerTest {

    /**
     * 验证并发连接无法突破单用户会话上限。
     *
     * @throws Exception 并发任务执行失败时抛出
     */
    @Test
    void shouldEnforcePerUserLimitAtomically() throws Exception {
        WsSessionManager manager = new WsSessionManager(1);
        WsSession first = session("session-1", "user-1");
        WsSession second = session("session-2", "user-1");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> registerFirst = () -> registerAfterBarrier(manager, first, barrier);
            Callable<Object> registerSecond = () -> registerAfterBarrier(manager, second, barrier);

            List<Future<Object>> futures = executor.invokeAll(List.of(registerFirst, registerSecond));
            List<Object> outcomes = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(outcomes).filteredOn(WsSession.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(WsException.class::isInstance)
                    .singleElement()
                    .satisfies(outcome -> assertThat(((WsException) outcome).getCode()).isEqualTo("WS_003"));
            assertThat(manager.getSessionCount()).isEqualTo(1);
            assertThat(manager.getUserSessions("user-1")).hasSize(1);
        } finally {
            executor.shutdownNow();
            manager.clearAll();
        }
    }

    /**
     * 验证会话使用原生 ID，重复清理不会遗留用户索引。
     */
    @Test
    void shouldRemoveSessionIndexesIdempotently() {
        WsSessionManager manager = new WsSessionManager(2);
        WsSession session = session("native-session", "user-1");
        manager.register(session);

        assertThat(session.getSessionId()).isEqualTo("native-session");
        assertThat(manager.remove("native-session")).isSameAs(session);
        assertThat(manager.remove("native-session")).isNull();
        assertThat(manager.getUserSessions("user-1")).isEmpty();
        assertThat(manager.getOnlineUserCount()).isZero();
    }

    /**
     * 同步并发任务起点后尝试注册会话。
     *
     * @param manager 会话管理器
     * @param session 待注册会话
     * @param barrier 并发起点屏障
     * @return 注册成功返回会话，失败返回异常
     * @throws Exception 等待屏障失败时抛出
     */
    private Object registerAfterBarrier(
            WsSessionManager manager,
            WsSession session,
            CyclicBarrier barrier) throws Exception {
        barrier.await();
        try {
            manager.register(session);
            return session;
        } catch (WsException exception) {
            return exception;
        }
    }

    /**
     * 创建打开状态的测试会话。
     *
     * @param sessionId 原生会话 ID
     * @param userId 用户标识
     * @return 测试会话
     */
    private WsSession session(String sessionId, String userId) {
        WebSocketSession nativeSession = mock(WebSocketSession.class);
        when(nativeSession.getId()).thenReturn(sessionId);
        when(nativeSession.isOpen()).thenReturn(true);
        return new WsSession(nativeSession, userId);
    }
}
