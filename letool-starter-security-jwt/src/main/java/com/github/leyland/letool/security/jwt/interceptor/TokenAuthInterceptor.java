package com.github.leyland.letool.security.jwt.interceptor;

import cn.hutool.core.util.StrUtil;
import com.github.leyland.letool.security.jwt.annotation.*;
import com.github.leyland.letool.security.jwt.config.SecurityJwtProperties;
import com.github.leyland.letool.security.jwt.constant.SecurityJwtConstant;
import com.github.leyland.letool.security.jwt.jwt.*;
import com.github.leyland.letool.security.jwt.service.*;
import com.github.leyland.letool.tool.api.IResultCode;
import com.github.leyland.letool.tool.api.ResponseEntity;
import com.github.leyland.letool.tool.api.SystemResultCode;
import com.github.leyland.letool.tool.exception.LetoolSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Token 认证拦截器
 * <p>
 * 用于纯 Token 模式下的认证拦截
 *
 * @author Rungo
 */
public class TokenAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthInterceptor.class);

    private final JwtTokenProvider tokenProvider;
    private final TokenStorageService tokenStorageService;
    private final JwtUserDetailsService userDetailsService;
    private final SecurityJwtProperties properties;

    public TokenAuthInterceptor(JwtTokenProvider tokenProvider,
                                TokenStorageService tokenStorageService,
                                JwtUserDetailsService userDetailsService,
                                SecurityJwtProperties properties) {
        this.tokenProvider = tokenProvider;
        this.tokenStorageService = tokenStorageService;
        this.userDetailsService = userDetailsService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理 Controller 方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 检查是否跳过认证
        if (shouldSkipAuth(handlerMethod)) {
            return true;
        }

        // 检查白名单路径
        if (isWhitePath(request.getRequestURI())) {
            return true;
        }

        // 获取 Token
        String authHeader = request.getHeader(tokenProvider.getHeaderName());
        String token = tokenProvider.extractToken(authHeader);

        if (StrUtil.isEmpty(token)) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "未提供认证Token");
        }

        // 解析 Token
        TokenPayload payload;
        try {
            payload = tokenProvider.parseToken(token);
        } catch (JwtTokenExpiredException e) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "Token已过期");
        } catch (JwtTokenException e) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "无效的Token");
        }

        // 验证 Token 类型
        if (!SecurityJwtConstant.TOKEN_TYPE_ACCESS.equals(payload.getTokenType())) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "非Access Token");
        }

        // 验证存储中是否存在 (如果启用缓存)
        if (properties.getStorage().isEnableCache() && !tokenStorageService.existsAccessToken(payload.getTokenId())) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "Token已失效");
        }

        // 检查用户状态
        if (userDetailsService.isUserDisabled(payload.getUserId())) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "用户已被禁用");
        }

        // 设置上下文
        SecurityContextHolder.setPayload(payload);

        // 检查权限注解
        checkPermission(handlerMethod, payload);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除上下文
        SecurityContextHolder.clear();
    }

    /**
     * 检查是否应该跳过认证
     */
    private boolean shouldSkipAuth(HandlerMethod handlerMethod) {
        // 检查方法上的 SkipAuth
        if (handlerMethod.getMethodAnnotation(SkipAuth.class) != null) {
            return true;
        }
        // 检查类上的 SkipAuth
        if (handlerMethod.getBeanType().getAnnotation(SkipAuth.class) != null) {
            return true;
        }
        return false;
    }

    /**
     * 检查是否是白名单路径
     */
    private boolean isWhitePath(String path) {
        String[] whitePaths = properties.getWhitePaths();
        if (whitePaths == null || whitePaths.length == 0) {
            return false;
        }
        for (String whitePath : whitePaths) {
            if (path.startsWith(whitePath) || path.equals(whitePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查权限注解
     */
    private void checkPermission(HandlerMethod handlerMethod, TokenPayload payload) {
        // 检查 RequireLogin (默认已登录才能到达这里)
        RequireLogin requireLogin = handlerMethod.getMethodAnnotation(RequireLogin.class);
        if (requireLogin == null) {
            requireLogin = handlerMethod.getBeanType().getAnnotation(RequireLogin.class);
        }

        // 检查 RequireRole
        if (properties.isEnableRole()) {
            RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null) {
                checkRole(payload, requireRole);
            }
        }

        // 检查 RequirePermission
        if (properties.isEnablePermission()) {
            RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
            if (requirePermission == null) {
                requirePermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
            }
            if (requirePermission != null) {
                checkPermission(payload, requirePermission);
            }
        }
    }

    /**
     * 检查角色
     */
    private void checkRole(TokenPayload payload, RequireRole requireRole) {
        String[] requiredRoles = requireRole.value();
        String[] userRoles = payload.getRoles();

        if (userRoles == null || userRoles.length == 0) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "用户无角色信息");
        }

        Set<String> userRoleSet = new HashSet<>(Arrays.asList(userRoles));

        if (requireRole.requireAll()) {
            // 需要满足所有角色
            for (String role : requiredRoles) {
                if (!userRoleSet.contains(role)) {
                    throw new LetoolSecurityException(SystemResultCode.REQ_REJECT, "缺少角色: " + role);
                }
            }
        } else {
            // 只需满足其中一个角色
            boolean hasRole = false;
            for (String role : requiredRoles) {
                if (userRoleSet.contains(role)) {
                    hasRole = true;
                    break;
                }
            }
            if (!hasRole) {
                throw new LetoolSecurityException(SystemResultCode.REQ_REJECT, "缺少所需角色");
            }
        }
    }

    /**
     * 检查权限
     */
    private void checkPermission(TokenPayload payload, RequirePermission requirePermission) {
        String[] requiredPermissions = requirePermission.value();
        String[] userPermissions = payload.getPermissions();

        if (userPermissions == null || userPermissions.length == 0) {
            throw new LetoolSecurityException(SystemResultCode.UN_AUTHORIZED, "用户无权限信息");
        }

        Set<String> userPermissionSet = new HashSet<>(Arrays.asList(userPermissions));

        if (requirePermission.requireAll()) {
            // 需要满足所有权限
            for (String permission : requiredPermissions) {
                if (!userPermissionSet.contains(permission)) {
                    throw new LetoolSecurityException(SystemResultCode.REQ_REJECT, "缺少权限: " + permission);
                }
            }
        } else {
            // 只需满足其中一个权限
            boolean hasPermission = false;
            for (String permission : requiredPermissions) {
                if (userPermissionSet.contains(permission)) {
                    hasPermission = true;
                    break;
                }
            }
            if (!hasPermission) {
                throw new LetoolSecurityException(SystemResultCode.REQ_REJECT, "缺少所需权限");
            }
        }
    }
}