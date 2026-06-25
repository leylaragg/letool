package com.github.leyland.letool.security.aspect;

import com.github.leyland.letool.security.annotation.RequirePermission;
import com.github.leyland.letool.security.annotation.RequireRole;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.security.util.SecurityUtil;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class SecurityAnnotationAspect {

    private static final Logger log = LoggerFactory.getLogger(SecurityAnnotationAspect.class);

    @Before("@within(requireRole) || @annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("未登录");
        }
        for (String role : requireRole.value()) {
            if (user.hasRole(role)) {
                return;
            }
        }
        log.debug("User {} lacks required role: {}", user.getUsername(), requireRole.value());
        throw new org.springframework.security.access.AccessDeniedException(
                "需要角色: " + String.join(", ", requireRole.value()));
    }

    @Before("@within(requirePermission) || @annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("未登录");
        }
        for (String perm : requirePermission.value()) {
            if (user.hasPermission(perm)) {
                return;
            }
        }
        log.debug("User {} lacks required permission: {}", user.getUsername(), requirePermission.value());
        throw new org.springframework.security.access.AccessDeniedException(
                "需要权限: " + String.join(", ", requirePermission.value()));
    }
}
