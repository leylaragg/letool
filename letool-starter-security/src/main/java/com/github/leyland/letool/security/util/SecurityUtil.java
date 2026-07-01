package com.github.leyland.letool.security.util;

import com.github.leyland.letool.security.context.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * 安全工具类，提供获取当前登录用户和权限判断的静态方法。
 *
 * <p>所有方法从 Spring Security 的 {@link SecurityContextHolder} 中读取当前认证信息，
 * 可在 Controller、Service 等任意层直接调用。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 获取当前登录用户。
     *
     * @return 当前用户信息，未登录返回 {@code null}
     */
    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    /** @return 当前用户 ID，未登录返回 {@code null} */
    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    /** @return 当前用户名，未登录返回 {@code null} */
    public static String getCurrentUsername() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * @param role 角色标识
     * @return {@code true} 如果拥有
     */
    public static boolean hasRole(String role) {
        LoginUser user = getCurrentUser();
        return user != null && user.hasRole(role);
    }

    /**
     * 判断当前用户是否拥有任一角色。
     *
     * @param roles 角色标识列表
     * @return {@code true} 如果拥有其中至少一个
     */
    public static boolean hasAnyRole(String... roles) {
        LoginUser user = getCurrentUser();
        if (user == null) return false;
        for (String role : roles) {
            if (user.hasRole(role)) return true;
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有指定权限。
     *
     * @param permission 权限标识
     * @return {@code true} 如果拥有
     */
    public static boolean hasPermission(String permission) {
        LoginUser user = getCurrentUser();
        return user != null && user.hasPermission(permission);
    }

    /**
     * 判断当前用户是否拥有任一权限。
     *
     * @param permissions 权限标识列表
     * @return {@code true} 如果拥有其中至少一个
     */
    public static boolean hasAnyPermission(String... permissions) {
        LoginUser user = getCurrentUser();
        if (user == null) return false;
        for (String perm : permissions) {
            if (user.hasPermission(perm)) return true;
        }
        return false;
    }

    /** @return 当前用户的角色列表，未登录返回空列表 */
    public static List<String> getCurrentRoles() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getRoles() : Collections.emptyList();
    }

    /** @return 当前用户的权限列表，未登录返回空列表 */
    public static List<String> getCurrentPermissions() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getPermissions() : Collections.emptyList();
    }

    /**
     * 判断当前请求是否已通过认证。
     *
     * @return {@code true} 如果已认证且 Principal 为 {@link LoginUser}
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof LoginUser;
    }
}
