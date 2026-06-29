package com.github.leyland.letool.websocket.auth;

import com.github.leyland.letool.tool.util.StrUtil;
import com.github.leyland.letool.websocket.config.WebSocketProperties;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

/**
 * WebSocket 握手拦截器，在握手阶段完成 Token 校验和用户身份注入。
 *
 * <p>该拦截器实现了 Spring WebSocket 的 {@link HandshakeInterceptor} 接口，
 * 在 WebSocket 连接建立的握手阶段拦截请求，完成以下工作：</p>
 * <ol>
 *   <li>从 URL 查询参数中提取 Token（参数名由 {@code letool.websocket.auth.tokenParam} 配置）</li>
 *   <li>校验 Token 有效性（当前版本为简化实现，仅校验 Token 非空）</li>
 *   <li>解析 Token 构建 {@link WsPrincipal} 并注入到会话属性中</li>
 * </ol>
 *
 * <p>{@code beforeHandshake} 返回 {@code true} 表示允许握手继续，
 * 返回 {@code false} 表示拒绝连接。</p>
 *
 * <p>使用示例（前端连接时携带 Token）：</p>
 * <pre>{@code
 * // JavaScript 连接示例
 * const ws = new WebSocket("ws://localhost:8080/ws?token=eyJhbGciOiJI...");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsHandshakeInterceptor.class);

    // ======================== 字段 ========================

    /** WebSocket 配置属性 */
    private final WebSocketProperties properties;

    // ======================== 构造 ========================

    /**
     * 创建握手拦截器。
     *
     * @param properties WebSocket 配置属性
     */
    public WsHandshakeInterceptor(WebSocketProperties properties) {
        this.properties = properties;
    }

    // ======================== 握手前处理 ========================

    /**
     * 在 WebSocket 握手之前执行。
     *
     * <p>从请求 URL 中提取 Token 参数，进行简单的身份验证：
     * <ul>
     *   <li>若鉴权未开启（{@code auth.enabled=false}），创建匿名 WsPrincipal 并放行</li>
     *   <li>若鉴权开启但未携带 Token，拒绝连接</li>
     *   <li>若携带 Token，创建对应用户的 WsPrincipal 并注入到 attributes</li>
     * </ul>
     * </p>
     *
     * @param request    握手请求
     * @param response   握手响应
     * @param wsHandler  目标 WebSocket 处理器
     * @param attributes 握手属性（可在此注入数据，供后续 WebSocketHandler 使用）
     * @return {@code true} 允许握手继续，{@code false} 拒绝连接
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        boolean authEnabled = properties.getAuth().isEnabled();

        if (!authEnabled) {
            // 鉴权未开启：创建匿名用户主体
            WsPrincipal anonymous = new WsPrincipal("anonymous-" + System.currentTimeMillis());
            attributes.put("principal", anonymous);
            log.debug("WebSocket handshake: auth disabled, anonymous user connected");
            return true;
        }

        // 提取 Token
        String tokenParam = properties.getAuth().getTokenParam();
        String token = extractQueryParam(request, tokenParam);

        if (StrUtil.isBlank(token)) {
            log.warn("WebSocket handshake rejected: no token provided (param={})", tokenParam);
            return false;
        }

        // 简单校验：Token 非空即认为有效
        // 实际项目中应在此处调用 JWT 解析或认证服务
        WsPrincipal principal = parseToken(token);
        if (principal == null) {
            log.warn("WebSocket handshake rejected: invalid token");
            return false;
        }

        // 注入用户主体到握手属性
        attributes.put("principal", principal);
        log.info("WebSocket handshake accepted: userId={}", principal.getUserId());
        return true;
    }

    // ======================== 握手后处理 ========================

    /**
     * 握手成功后回调（当前为空操作）。
     *
     * @param request   握手请求
     * @param response  握手响应
     * @param wsHandler 目标 WebSocket 处理器
     * @param exception 握手过程中发生的异常，正常完成时为 {@code null}
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake completed with error: {}", exception.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 从请求 URL 中提取指定名称的查询参数。
     *
     * @param request   握手请求
     * @param paramName 参数名
     * @return 参数值，不存在返回 {@code null}
     */
    private String extractQueryParam(ServerHttpRequest request, String paramName) {
        URI uri = request.getURI();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (StrUtil.isBlank(query)) return null;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && paramName.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * 解析 Token 生成 WsPrincipal。
     *
     * <p>当前版本为简化实现，直接以 Token 的 hashCode 作为 userId。
     * 实际项目应集成 JWT 解析或调用认证服务来获取完整的用户信息。</p>
     *
     * @param token 鉴权 Token
     * @return WsPrincipal 实例，解析失败返回 {@code null}
     */
    private WsPrincipal parseToken(String token) {
        if (StrUtil.isBlank(token)) return null;
        // 简化实现：将 Token 作为用户标识
        // 实际项目应解析 JWT 获取真实的 userId、username、roles
        try {
            String userId = "user_" + Math.abs(token.hashCode() % 100000);
            return new WsPrincipal(userId, userId, Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to parse token: {}", e.getMessage());
            return null;
        }
    }
}
