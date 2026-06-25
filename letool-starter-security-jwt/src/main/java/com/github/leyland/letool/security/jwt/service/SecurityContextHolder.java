package com.github.leyland.letool.security.jwt.service;

import com.github.leyland.letool.security.jwt.jwt.TokenPayload;

/**
 * 安全上下文持有者
 * <p>
 * 用于存储当前请求的认证信息
 *
 * @author Rungo
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<TokenPayload> CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前请求的 Token Payload
     *
     * @param payload Token Payload
     */
    public static void setPayload(TokenPayload payload) {
        CONTEXT.set(payload);
    }

    /**
     * 获取当前请求的 Token Payload
     *
     * @return Token Payload 或 null
     */
    public static TokenPayload getPayload() {
        return CONTEXT.get();
    }

    /**
     * 清除当前请求的 Token Payload
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID 或 null
     */
    public static Long getCurrentUserId() {
        TokenPayload payload = getPayload();
        return payload != null ? payload.getUserId() : null;
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名 或 null
     */
    public static String getCurrentUsername() {
        TokenPayload payload = getPayload();
        return payload != null ? payload.getUsername() : null;
    }

    /**
     * 获取当前用户角色
     *
     * @return 角色数组 或 null
     */
    public static String[] getCurrentRoles() {
        TokenPayload payload = getPayload();
        return payload != null ? payload.getRoles() : null;
    }

    /**
     * 获取当前用户权限
     *
     * @return 权限数组 或 null
     */
    public static String[] getCurrentPermissions() {
        TokenPayload payload = getPayload();
        return payload != null ? payload.getPermissions() : null;
    }

    /**
     * 检查是否已认证
     *
     * @return 是否已认证
     */
    public static boolean isAuthenticated() {
        return getPayload() != null;
    }

    private SecurityContextHolder() {
    }
}