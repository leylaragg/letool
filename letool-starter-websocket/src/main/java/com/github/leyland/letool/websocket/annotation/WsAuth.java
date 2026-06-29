package com.github.leyland.letool.websocket.annotation;

import java.lang.annotation.*;

/**
 * WebSocket 消息处理鉴权注解，用于声明某个消息处理方法需要特定的角色权限。
 *
 * <p>被此注解标注的消息处理方法（标注了 {@link WsMessageMapping} 的方法），
 * 在收到消息后会先检查当前会话的 {@code WsPrincipal} 是否拥有所需的角色。
 * 若不满足条件，则拒绝处理该消息。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 仅管理员可处理
 * @WsAuth(roles = {"admin"})
 * @WsMessageMapping("system:shutdown")
 * public void handleShutdown(WsSession session, WsMessage message) { ... }
 *
 * // 需登录但无具体角色要求
 * @WsAuth(required = true)
 * @WsMessageMapping("subscribe")
 * public void handleSubscribe(WsSession session, WsMessage message) { ... }
 *
 * // 无需鉴权（允许匿名访问）
 * @WsAuth(required = false)
 * @WsMessageMapping("public:ping")
 * public void handlePing(WsSession session, WsMessage message) { ... }
 * }</pre>
 *
 * <p>注意：当前版本中，权限检查主要在 {@code WsHandshakeInterceptor} 握手阶段完成。
 * 此注解留作后续扩展（如基于注解的细粒度方法级权限控制）。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsAuth {

    /**
     * 所需角色列表。
     *
     * <p>当前会话的用户必须拥有所有指定角色才能处理该消息。
     * 默认为空数组，表示不限制角色。</p>
     *
     * @return 角色名称数组，如 {"admin", "moderator"}
     */
    String[] roles() default {};

    /**
     * 是否必须已认证（已登录）。
     *
     * <p>如果设为 {@code true}，则要求当前会话的 {@code WsPrincipal} 非 {@code null}
     * 且 userId 不为空。如果设为 {@code false}，则允许匿名用户（未携带 Token）处理该消息。</p>
     *
     * @return {@code true} 如果要求必须认证，默认 {@code true}
     */
    boolean required() default true;
}
