package io.github.leylaragg.letool.sample.controller;

import io.github.leylaragg.letool.sample.model.LoginRequest;
import io.github.leylaragg.letool.security.annotation.RequireRole;
import io.github.leylaragg.letool.security.context.LoginUser;
import io.github.leylaragg.letool.security.jwt.JwtTokenProvider;
import io.github.leylaragg.letool.security.util.SecurityUtil;
import io.github.leylaragg.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示 letool-starter-security JWT 认证与权限控制。
 */
@RestController
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 登录并返回 JWT Token。
     *
     * <p>公开访问由 {@code letool.security.exclude-paths} 配置，不依赖无效的控制器标记。</p>
     *
     * @param request 登录请求
     * @return AccessToken 和 RefreshToken
     */
    @PostMapping("/api/auth/login")
    public R<Map<String, String>> login(@RequestBody LoginRequest request) {
        // 简化演示：直接创建用户，不查数据库
        LoginUser user = new LoginUser(
                1L, request.getUsername(),
                List.of("USER", "ADMIN"),
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
     * 获取当前用户信息，需要在 Authorization Header 中传入 Bearer Token。
     *
     * @return 当前用户身份、角色和权限
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
     * 管理员接口，仅 ADMIN 角色可访问。
     *
     * @return 管理员控制台说明
     */
    @RequireRole("ADMIN")
    @GetMapping("/api/admin/dashboard")
    public R<String> dashboard() {
        return R.ok("管理员控制台——只有 ADMIN 角色能访问");
    }

    /**
     * 公开健康检查接口。
     *
     * <p>公开访问由 {@code letool.security.exclude-paths} 中的
     * {@code /api/public/**} 声明。</p>
     *
     * @return Sample 健康状态
     */
    @GetMapping("/api/public/health")
    public R<String> health() {
        return R.ok("letool-sample is running");
    }
}
