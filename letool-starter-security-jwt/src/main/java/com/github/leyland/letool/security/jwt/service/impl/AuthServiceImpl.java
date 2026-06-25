package com.github.leyland.letool.security.jwt.service.impl;

import com.github.leyland.letool.security.jwt.config.SecurityJwtProperties;
import com.github.leyland.letool.security.jwt.constant.SecurityJwtConstant;
import com.github.leyland.letool.security.jwt.jwt.*;
import com.github.leyland.letool.security.jwt.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证服务实现
 *
 * @author Rungo
 */
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final JwtTokenProvider tokenProvider;
    private final JwtUserDetailsService userDetailsService;
    private final TokenStorageService tokenStorageService;
    private final SecurityJwtProperties properties;

    public AuthServiceImpl(JwtTokenProvider tokenProvider,
                           JwtUserDetailsService userDetailsService,
                           TokenStorageService tokenStorageService,
                           SecurityJwtProperties properties) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenStorageService = tokenStorageService;
        this.properties = properties;
    }

    @Override
    public AuthInfo login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // 加载用户信息
        JwtUser jwtUser = userDetailsService.loadUserByUsername(username);
        if (jwtUser == null) {
            throw new AuthenticationException("用户不存在: " + username);
        }

        // 验证密码
        if (!userDetailsService.verifyPassword(username, password)) {
            throw new AuthenticationException("密码错误");
        }

        // 检查用户状态
        if (userDetailsService.isUserDisabled(jwtUser.getId())) {
            throw new AuthenticationException("用户已被禁用");
        }

        // 设置用户状态
        jwtUser.setStatus(SecurityJwtConstant.STATUS_NORMAL);

        // 生成 Token
        AuthInfo authInfo = tokenProvider.generateAuthInfo(jwtUser);

        // 存储 Token
        TokenPayload payload = tokenProvider.parseToken(authInfo.getAccessToken());
        tokenStorageService.storeAccessToken(jwtUser.getId(), payload.getTokenId(),
                authInfo.getAccessToken(), properties.getJwt().getAccessTokenExpiration());

        TokenPayload refreshPayload = tokenProvider.parseToken(authInfo.getRefreshToken());
        tokenStorageService.storeRefreshToken(jwtUser.getId(), refreshPayload.getTokenId(),
                authInfo.getRefreshToken(), properties.getJwt().getRefreshTokenExpiration());

        log.info("用户登录成功: username={}, userId={}", username, jwtUser.getId());
        return authInfo;
    }

    @Override
    public AuthInfo refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 解析 Refresh Token
        TokenPayload payload = tokenProvider.parseToken(refreshToken);

        // 验证 Token 类型
        if (!SecurityJwtConstant.TOKEN_TYPE_REFRESH.equals(payload.getTokenType())) {
            throw new JwtTokenInvalidException("非Refresh Token");
        }

        // 检查存储中是否存在
        if (!tokenStorageService.existsRefreshToken(payload.getTokenId())) {
            throw new JwtTokenInvalidException("Refresh Token已失效");
        }

        // 加载用户信息
        JwtUser jwtUser = userDetailsService.loadUserById(payload.getUserId());
        if (jwtUser == null) {
            throw new AuthenticationException("用户不存在");
        }

        // 检查用户状态
        if (userDetailsService.isUserDisabled(jwtUser.getId())) {
            throw new AuthenticationException("用户已被禁用");
        }

        // 生成新的 Token
        AuthInfo authInfo = tokenProvider.generateAuthInfo(jwtUser);

        // 移除旧的 Refresh Token
        tokenStorageService.removeToken(payload.getTokenId());

        // 存储新的 Token
        TokenPayload newPayload = tokenProvider.parseToken(authInfo.getAccessToken());
        tokenStorageService.storeAccessToken(jwtUser.getId(), newPayload.getTokenId(),
                authInfo.getAccessToken(), properties.getJwt().getAccessTokenExpiration());

        TokenPayload newRefreshPayload = tokenProvider.parseToken(authInfo.getRefreshToken());
        tokenStorageService.storeRefreshToken(jwtUser.getId(), newRefreshPayload.getTokenId(),
                authInfo.getRefreshToken(), properties.getJwt().getRefreshTokenExpiration());

        log.info("Token刷新成功: userId={}", jwtUser.getId());
        return authInfo;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        try {
            TokenPayload payload = tokenProvider.parseToken(token);
            tokenStorageService.removeToken(payload.getTokenId());
            log.info("用户登出: userId={}", payload.getUserId());
        } catch (JwtTokenException e) {
            log.warn("登出时Token解析失败: {}", e.getMessage());
        }
    }

    @Override
    public void kickout(Long userId) {
        tokenStorageService.removeAllTokens(userId);
        log.info("踢出用户: userId={}", userId);
    }

    @Override
    public JwtUser getCurrentUser() {
        TokenPayload payload = SecurityContextHolder.getPayload();
        if (payload == null) {
            return null;
        }
        try {
            return userDetailsService.loadUserById(payload.getUserId());
        } catch (UserNotFoundException e) {
            return null;
        }
    }

    @Override
    public Long getCurrentUserId() {
        TokenPayload payload = SecurityContextHolder.getPayload();
        return payload != null ? payload.getUserId() : null;
    }

    @Override
    public boolean isAuthenticated() {
        return SecurityContextHolder.getPayload() != null;
    }
}