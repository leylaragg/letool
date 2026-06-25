package com.github.leyland.letool.security.jwt.controller;

import com.github.leyland.letool.security.jwt.annotation.SkipAuth;
import com.github.leyland.letool.security.jwt.jwt.AuthInfo;
import com.github.leyland.letool.security.jwt.jwt.LoginRequest;
import com.github.leyland.letool.security.jwt.jwt.RefreshTokenRequest;
import com.github.leyland.letool.security.jwt.service.AuthService;
import com.github.leyland.letool.tool.api.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器示例
 * <p>
 * 提供登录、刷新 Token、登出等接口
 * 可根据实际需求进行扩展或重写
 *
 * @author Rungo
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 认证信息
     */
    @SkipAuth
    @PostMapping("/login")
    public ResponseEntity<AuthInfo> login(@RequestBody LoginRequest request) {
        AuthInfo authInfo = authService.login(request);
        return ResponseEntity.data(authInfo);
    }

    /**
     * 刷新 Token
     *
     * @param request 刷新请求
     * @return 新的认证信息
     */
    @SkipAuth
    @PostMapping("/refresh")
    public ResponseEntity<AuthInfo> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthInfo authInfo = authService.refreshToken(request);
        return ResponseEntity.data(authInfo);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.success("登出成功");
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        return ResponseEntity.data(authService.getCurrentUser());
    }
}