package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.sample.model.LoginRequest;
import com.github.leyland.letool.security.annotation.SkipAuth;
import com.github.leyland.letool.security.annotation.RequireRole;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import com.github.leyland.letool.security.util.SecurityUtil;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示 letool-starter-security JWT 认证与权限控制.
 */
@RestController
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 登录 —— 跳过认证，返回 JWT token.
     */
    @SkipAuth
    @PostMapping("/api/auth/login")
    public R<Map<String, String>> login(@RequestBody LoginRequest request) {
        // 简化演示：直接创建用户，不查数据库
        LoginUser user = new LoginUser(
                1L, request.getUsername(),
                List.of("ROLE_USER", "ROLE_ADMIN"),
                List.of("user:read", "user:write")
        );
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        return R.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer"
        ));
    }

    /**
     * 获取当前用户信息 —— 需要在 Authorization Header 中传入 Bearer token.
     */
    @GetMapping("/api/user/me")
    public R<Map<String, Object>> me() {
        LoginUser user = SecurityUtil.getCurrentUser();
        return R.ok(Map.of(
                "userId", user.getUserId(),
                "username", user.getUsername(),
                "roles", user.getRoles(),
                "permissions", user.getPermissions()
        ));
    }

    /**
     * 管理员接口 —— 仅 ADMIN 角色可访问.
     */
    @RequireRole("ROLE_ADMIN")
    @GetMapping("/api/admin/dashboard")
    public R<String> dashboard() {
        return R.ok("管理员控制台——只有 ADMIN 角色能访问");
    }

    /**
     * 公开接口 —— 无需认证.
     */
    @SkipAuth
    @GetMapping("/api/public/health")
    public R<String> health() {
        return R.ok("letool-sample is running");
    }
}
