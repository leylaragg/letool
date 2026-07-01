package com.github.leyland.letool.security.aspect;

import com.github.leyland.letool.security.annotation.RequirePermission;
import com.github.leyland.letool.security.annotation.RequireRole;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.security.util.SecurityUtil;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全注解切面，拦截 {@link RequireRole} 和 {@link RequirePermission} 注解。
 *
 * <p>支持标注在类或方法上，类级注解对该类所有方法生效。
 * 不满足角色/权限要求时抛出 {@link org.springframework.security.access.AccessDeniedException}。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Aspect
public class SecurityAnnotationAspect {

    private static final Logger log = LoggerFactory.getLogger(SecurityAnnotationAspect.class);

    /**
     * 检查 {@link RequireRole} 注解 —— 当前用户必须拥有指定角色之一。
     *
     * @param requireRole 角色要求注解
     * @throws org.springframework.security.access.AccessDeniedException 如果未登录或不满足角色要求
     */
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

    /**
     * 检查 {@link RequirePermission} 注解 —— 当前用户必须拥有指定权限之一。
     *
     * @param requirePermission 权限要求注解
     * @throws org.springframework.security.access.AccessDeniedException 如果未登录或不满足权限要求
     */
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
