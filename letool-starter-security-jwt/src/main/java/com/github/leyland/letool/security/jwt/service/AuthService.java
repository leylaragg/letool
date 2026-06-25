package com.github.leyland.letool.security.jwt.service;

import com.github.leyland.letool.security.jwt.jwt.*;

/**
 * 认证服务接口
 *
 * @author Rungo
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 认证信息
     * @throws AuthenticationException 认证失败
     */
    AuthInfo login(LoginRequest request);

    /**
     * 刷新 Token
     *
     * @param request 刷新请求
     * @return 新的认证信息
     * @throws JwtTokenExpiredException Refresh Token 过期
     */
    AuthInfo refreshToken(RefreshTokenRequest request);

    /**
     * 用户登出
     *
     * @param token Access Token
     */
    void logout(String token);

    /**
     * 踢出指定用户的所有会话
     *
     * @param userId 用户ID
     */
    void kickout(Long userId);

    /**
     * 获取当前登录用户信息
     *
     * @return JwtUser 或 null
     */
    JwtUser getCurrentUser();

    /**
     * 获取当前用户ID
     *
     * @return 用户ID 或 null
     */
    Long getCurrentUserId();

    /**
     * 检查当前用户是否已认证
     *
     * @return 是否已认证
     */
    boolean isAuthenticated();
}