package com.github.leyland.letool.websocket.auth;

import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.exception.WsException;
import org.springframework.http.server.ServerHttpRequest;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * 复用 HTTP 握手请求既有主体的默认认证器。
 *
 * <p>该实现不解析 Token。应用使用 Spring Security 时可直接复用认证结果；没有建立
 * HTTP 主体的请求会被拒绝。需要其他凭据形式时应提供自定义 {@link WsAuthenticator}。</p>
 */
public final class PrincipalWsAuthenticator implements WsAuthenticator {

    /**
     * 从 HTTP 请求读取可信主体。
     *
     * @param request WebSocket 握手请求
     * @return WebSocket 用户主体
     * @throws WsException HTTP 请求没有可信主体时抛出
     */
    @Override
    public WsPrincipal authenticate(ServerHttpRequest request) {
        Principal principal = request == null ? null : request.getPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw WsException.authenticationFailed();
        }
        return new WsPrincipal(principal.getName(), principal.getName(), Set.of(), Map.of());
    }
}
