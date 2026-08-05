package com.github.leyland.letool.websocket.auth;

import com.github.leyland.letool.tool.annotation.SPI;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.exception.WsException;
import org.springframework.http.server.ServerHttpRequest;

/**
 * WebSocket 握手认证扩展接口。
 *
 * <p>应用可以读取已经由 Spring Security 建立的主体，也可以从请求头、Cookie 或查询参数
 * 中解析业务凭据。实现不得把原始凭据写入 {@link WsPrincipal} 扩展属性或日志。</p>
 */
@SPI
@FunctionalInterface
public interface WsAuthenticator {

    /**
     * 认证一次 WebSocket 握手请求。
     *
     * @param request WebSocket 握手请求
     * @return 可信且不可变的用户主体
     * @throws WsException 请求未通过认证时抛出
     */
    WsPrincipal authenticate(ServerHttpRequest request);
}
