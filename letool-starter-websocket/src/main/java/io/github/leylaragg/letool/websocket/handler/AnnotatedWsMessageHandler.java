package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.websocket.annotation.WsAuth;
import io.github.leylaragg.letool.websocket.auth.WsHandshakeInterceptor;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsPrincipal;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 将一个已经校验的注解方法适配为消息处理器。
 */
final class AnnotatedWsMessageHandler implements WsMessageHandler {

    private final Object bean;
    private final Method method;
    private final String messageType;
    private final WsAuth authorization;

    /**
     * 创建注解方法处理器。
     *
     * @param bean 目标 Bean
     * @param method 可调用方法
     * @param messageType 消息类型
     * @param authorization 授权约束，可为 {@code null}
     */
    AnnotatedWsMessageHandler(
            Object bean,
            Method method,
            String messageType,
            WsAuth authorization) {
        this.bean = Objects.requireNonNull(bean, "bean must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.messageType = messageType;
        this.authorization = authorization;
    }

    /**
     * 执行授权检查并调用目标方法。
     *
     * @param session 当前会话
     * @param message 入站消息
     */
    @Override
    public void handle(WsSession session, WsMessage message) {
        authorize(session);
        try {
            method.invoke(bean, session, message);
        } catch (IllegalAccessException exception) {
            throw WsException.configurationInvalid("注解消息处理方法不可访问");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw WsException.handlerFailed(messageType, cause);
        }
    }

    /**
     * 获取消息类型。
     *
     * @return 消息类型
     */
    @Override
    public String getMessageType() {
        return messageType;
    }

    /**
     * 校验注解授权约束。
     *
     * @param session 当前会话
     */
    private void authorize(WsSession session) {
        if (authorization == null) {
            return;
        }
        WsPrincipal principal = session.getAttribute(WsHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        boolean requiresAuthentication = authorization.required() || authorization.roles().length > 0;
        if (requiresAuthentication && (principal == null || !principal.isAuthenticated())) {
            throw WsException.accessDenied(messageType);
        }
        if (principal != null && !principal.hasAllRoles(authorization.roles())) {
            throw WsException.accessDenied(messageType);
        }
    }
}
