package com.github.leyland.letool.websocket.auth;

import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.exception.WsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 在 WebSocket 握手阶段执行真实身份认证的拦截器。
 */
public final class WsHandshakeInterceptor implements HandshakeInterceptor {

    /** 会话属性中的用户主体键。 */
    public static final String PRINCIPAL_ATTRIBUTE = "letool.websocket.principal";

    private static final Logger log = LoggerFactory.getLogger(WsHandshakeInterceptor.class);

    private final WebSocketProperties properties;
    private final WsAuthenticator authenticator;

    /**
     * 创建握手认证拦截器。
     *
     * @param properties WebSocket 配置
     * @param authenticator 实际认证器
     */
    public WsHandshakeInterceptor(WebSocketProperties properties, WsAuthenticator authenticator) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
    }

    /**
     * 认证握手请求并注入不可变主体。
     *
     * @param request 握手请求
     * @param response 握手响应
     * @param wsHandler WebSocket 处理器
     * @param attributes 会话属性
     * @return 允许继续握手时返回 {@code true}
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!properties.getAuth().isEnabled()) {
            attributes.put(PRINCIPAL_ATTRIBUTE, WsPrincipal.anonymous(UUID.randomUUID().toString()));
            return true;
        }
        try {
            WsPrincipal principal = authenticator.authenticate(request);
            if (principal == null || !principal.isAuthenticated()) {
                throw WsException.authenticationFailed();
            }
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
            log.debug("WebSocket 握手认证通过，userId={}", principal.getUserId());
            return true;
        } catch (WsException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("WebSocket 握手认证失败，code={}", exception.getCode());
            return false;
        } catch (RuntimeException exception) {
            // 自定义认证器异常时必须关闭握手，不能因扩展实现错误绕过鉴权。
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("WebSocket 握手认证器执行失败，type={}", exception.getClass().getSimpleName());
            log.debug("WebSocket 握手认证器执行失败详情", exception);
            return false;
        }
    }

    /**
     * 握手结束后的空回调。
     *
     * @param request 握手请求
     * @param response 握手响应
     * @param wsHandler WebSocket 处理器
     * @param exception 握手异常，正常完成时为 {@code null}
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.warn("WebSocket 握手过程异常，type={}", exception.getClass().getSimpleName());
        }
    }
}
