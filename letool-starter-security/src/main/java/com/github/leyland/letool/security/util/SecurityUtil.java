package com.github.leyland.letool.security.util;

import com.github.leyland.letool.security.context.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

public final class SecurityUtil {

    private SecurityUtil() {}

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentUsername() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public static boolean hasRole(String role) {
        LoginUser user = getCurrentUser();
        return user != null && user.hasRole(role);
    }

    public static boolean hasAnyRole(String... roles) {
        LoginUser user = getCurrentUser();
        if (user == null) return false;
        for (String role : roles) {
            if (user.hasRole(role)) return true;
        }
        return false;
    }

    public static boolean hasPermission(String permission) {
        LoginUser user = getCurrentUser();
        return user != null && user.hasPermission(permission);
    }

    public static boolean hasAnyPermission(String... permissions) {
        LoginUser user = getCurrentUser();
        if (user == null) return false;
        for (String perm : permissions) {
            if (user.hasPermission(perm)) return true;
        }
        return false;
    }

    public static List<String> getCurrentRoles() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getRoles() : Collections.emptyList();
    }

    public static List<String> getCurrentPermissions() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getPermissions() : Collections.emptyList();
    }

    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof LoginUser;
    }
}
