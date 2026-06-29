package com.github.leyland.letool.websocket.annotation;

import java.lang.annotation.*;

/**
 * WebSocket 消息路由注解，用于标记一个方法可以处理特定类型的 WebSocket 消息。
 *
 * <p>该注解用于标注方法级别的消息处理器，与 Spring 的 {@code @MessageMapping} 类似，
 * 但专注于 letool WebSocket 模块的消息路由。</p>
 *
 * <p>被标注的方法应接受 {@code WsSession} 和 {@code WsMessage} 两个参数：</p>
 * <pre>{@code
 * @WsMessageMapping("chat")
 * public void handleChat(WsSession session, WsMessage message) {
 *     // 处理聊天消息
 * }
 * }</pre>
 *
 * <p>注意：当前版本中，消息路由主要通过实现 {@code WsMessageHandler} 接口完成。
 * 此注解留作后续扩展（如基于注解的自动发现和注册机制）。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsMessageMapping {

    /**
     * 消息类型标识（路由值）。
     *
     * <p>该值与 {@code WsMessage.type} 字段精确匹配，决定哪些消息会被路由到此方法。</p>
     *
     * @return 消息类型字符串，如 "chat"、"notification"、"subscribe"
     */
    String value();

    /**
     * 方法描述，用于日志和文档。
     *
     * @return 描述文本，默认为空字符串
     */
    String description() default "";
}
