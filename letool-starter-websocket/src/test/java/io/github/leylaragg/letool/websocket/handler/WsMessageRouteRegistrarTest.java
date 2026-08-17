package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.websocket.annotation.WsAuth;
import io.github.leylaragg.letool.websocket.annotation.WsMessageMapping;
import io.github.leylaragg.letool.websocket.auth.WsHandshakeInterceptor;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsPrincipal;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 注解消息路由的注册、冲突和授权契约测试。
 */
class WsMessageRouteRegistrarTest {

    /**
     * 验证程序化与注解路由发生重名时启动注册立即失败。
     */
    @Test
    void shouldRejectDuplicateMessageType() {
        DefaultWsMessageRouter router = new DefaultWsMessageRouter();
        router.register("chat", handler("chat"));

        assertThatThrownBy(() -> router.register("chat", handler("chat")))
                .isInstanceOfSatisfying(WsException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WS_005"));
    }

    /**
     * 验证注解路由真实执行角色授权并调用目标方法。
     */
    @Test
    void shouldInvokeAuthorizedAnnotatedMethodAndRejectAnonymousSession() {
        DefaultWsMessageRouter router = new DefaultWsMessageRouter();
        WsMessageRouteRegistrar registrar = new WsMessageRouteRegistrar(router);
        AdminEndpoint endpoint = new AdminEndpoint();
        registrar.registerBean(endpoint);
        WsSession adminSession = session("admin-session",
                new WsPrincipal("admin-1", "管理员", Set.of("admin"), Map.of()));

        router.route(adminSession, WsMessage.of("admin:refresh", "payload"));

        assertThat(endpoint.invocations.get()).isEqualTo(1);
        WsSession anonymousSession = session("anonymous-session", WsPrincipal.anonymous("anonymous-1"));
        assertThatThrownBy(() -> router.route(
                anonymousSession, WsMessage.of("admin:refresh", "payload")))
                .isInstanceOfSatisfying(WsException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WS_006"));
        assertThat(endpoint.invocations.get()).isEqualTo(1);
    }

    /**
     * 验证非法注解方法签名不会延迟到收到消息后才失败。
     */
    @Test
    void shouldRejectInvalidAnnotatedMethodSignatureAtRegistration() {
        WsMessageRouteRegistrar registrar = new WsMessageRouteRegistrar(new DefaultWsMessageRouter());

        assertThatThrownBy(() -> registrar.registerBean(new InvalidEndpoint()))
                .isInstanceOfSatisfying(WsException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WS_001"));
    }

    /**
     * 创建仅用于冲突检测的处理器。
     *
     * @param type 消息类型
     * @return 消息处理器
     */
    private WsMessageHandler handler(String type) {
        return new WsMessageHandler() {
            @Override
            public void handle(WsSession session, WsMessage message) {
                // 冲突检测不需要业务行为。
            }

            @Override
            public String getMessageType() {
                return type;
            }
        };
    }

    /**
     * 创建绑定指定主体的测试会话。
     *
     * @param sessionId 会话 ID
     * @param principal 用户主体
     * @return 测试会话
     */
    private WsSession session(String sessionId, WsPrincipal principal) {
        WebSocketSession nativeSession = mock(WebSocketSession.class);
        when(nativeSession.getId()).thenReturn(sessionId);
        when(nativeSession.isOpen()).thenReturn(true);
        WsSession session = new WsSession(nativeSession, principal.getUserId());
        session.setAttribute(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
        return session;
    }

    /**
     * 需要管理员角色的注解端点。
     */
    static final class AdminEndpoint {

        private final AtomicInteger invocations = new AtomicInteger();

        /**
         * 处理管理员刷新消息。
         *
         * @param session 当前会话
         * @param message 入站消息
         */
        @WsAuth(roles = "admin")
        @WsMessageMapping("admin:refresh")
        public void refresh(WsSession session, WsMessage message) {
            invocations.incrementAndGet();
        }
    }

    /**
     * 包含非法签名的注解端点。
     */
    static final class InvalidEndpoint {

        /**
         * 返回值不符合注解路由契约。
         *
         * @return 无效返回值
         */
        @WsMessageMapping("invalid")
        public String invalid() {
            return "invalid";
        }
    }
}
