package com.github.leyland.letool.security.jwt.util;

import com.github.leyland.letool.security.jwt.jwt.JwtUser;
import com.github.leyland.letool.security.jwt.jwt.TokenPayload;
import com.github.leyland.letool.security.jwt.service.SecurityContextHolder;

/**
 * 登录用户工具类
 * <p>
 * 可在任何地方调用获取当前登录用户信息
 *
 * @author Rungo
 */
public final class LoginUserUtil {

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未登录返回 null
     */
    public static Long getUserId() {
        return SecurityContextHolder.getCurrentUserId();
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名，未登录返回 null
     */
    public static String getUsername() {
        return SecurityContextHolder.getCurrentUsername();
    }

    /**
     * 获取当前登录用户角色列表
     *
     * @return 角色数组，未登录返回 null
     */
    public static String[] getRoles() {
        return SecurityContextHolder.getCurrentRoles();
    }

    /**
     * 获取当前登录用户权限列表
     *
     * @return 权限数组，未登录返回 null
     */
    public static String[] getPermissions() {
        return SecurityContextHolder.getCurrentPermissions();
    }

    /**
     * 检查当前用户是否已登录
     *
     * @return true 已登录，false 未登录
     */
    public static boolean isLogin() {
        return SecurityContextHolder.isAuthenticated();
    }

    /**
     * 检查当前用户是否拥有指定角色
     *
     * @param role 角色名称
     * @return true 有该角色，false 无该角色
     */
    public static boolean hasRole(String role) {
        String[] roles = getRoles();
        if (roles == null || roles.length == 0) {
            return false;
        }
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前用户是否拥有指定权限
     *
     * @param permission 权限名称
     * @return true 有该权限，false 无该权限
     */
    public static boolean hasPermission(String permission) {
        String[] permissions = getPermissions();
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String p : permissions) {
            if (p.equals(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 Token Payload 信息
     *
     * @return TokenPayload，未登录返回 null
     */
    public static TokenPayload getPayload() {
        return SecurityContextHolder.getPayload();
    }

    /**
     * 获取用户信息 (需通过 JwtUserDetailsService 加载)
     * <p>
     * 注意：此方法需要注入 AuthService 或 JwtUserDetailsService
     * 建议直接使用 getUserId() / getUsername() 获取基本信息
     *
     * @return JwtUser，未登录返回 null
     */
    public static JwtUser getUser() {
        // 仅返回基本信息，完整用户信息需要通过 AuthService 获取
        TokenPayload payload = SecurityContextHolder.getPayload();
        if (payload == null) {
            return null;
        }
        JwtUser user = new JwtUser(payload.getUserId(), payload.getUsername());
        user.setRoles(payload.getRoles() != null ? java.util.Arrays.asList(payload.getRoles()) : null);
        user.setPermissions(payload.getPermissions() != null ? java.util.Arrays.asList(payload.getPermissions()) : null);
        return user;
    }

    private LoginUserUtil() {
    }
}