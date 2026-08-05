package com.github.leyland.letool.websocket.auth;

import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link WsHandshakeInterceptor} 的安全握手契约测试。
 */
class WsHandshakeInterceptorTest {

    /**
     * 验证鉴权开启时只接受认证器返回的可信主体。
     */
    @Test
    void shouldUseAuthenticatorResultInsteadOfTrustingQueryToken() {
        WebSocketProperties properties = new WebSocketProperties();
        WsPrincipal principal = new WsPrincipal("user-1", "张三", Set.of("admin"), Map.of("tenant", "t1"));
        WsAuthenticator authenticator = request -> principal;
        WsHandshakeInterceptor interceptor = new WsHandshakeInterceptor(properties, authenticator);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE)).isSameAs(principal);
    }

    /**
     * 验证鉴权开启时认证器返回匿名主体也会拒绝握手。
     */
    @Test
    void shouldRejectHandshakeWhenAuthenticatorFails() {
        WebSocketProperties properties = new WebSocketProperties();
        WsAuthenticator authenticator = request -> WsPrincipal.anonymous("anonymous-1");
        WsHandshakeInterceptor interceptor = new WsHandshakeInterceptor(properties, authenticator);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), response, mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isFalse();
        assertThat(attributes).doesNotContainKey(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    /**
     * 验证显式关闭鉴权时生成匿名主体且不调用认证器。
     */
    @Test
    void shouldCreateAnonymousPrincipalOnlyWhenAuthenticationIsDisabled() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.getAuth().setEnabled(false);
        WsAuthenticator authenticator = mock(WsAuthenticator.class);
        WsHandshakeInterceptor interceptor = new WsHandshakeInterceptor(properties, authenticator);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE))
                .isInstanceOfSatisfying(WsPrincipal.class,
                        principal -> assertThat(principal.getUserId()).startsWith("anonymous:"));
        verifyNoInteractions(authenticator);
    }
}
