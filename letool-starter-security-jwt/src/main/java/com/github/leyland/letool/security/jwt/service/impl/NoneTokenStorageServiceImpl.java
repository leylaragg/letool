package com.github.leyland.letool.security.jwt.service.impl;

import com.github.leyland.letool.security.jwt.service.TokenStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无存储的 Token 存储服务实现
 * <p>
 * 仅验证 Token 签名，不进行存储校验
 *
 * @author Rungo
 */
public class NoneTokenStorageServiceImpl implements TokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(NoneTokenStorageServiceImpl.class);

    @Override
    public void storeAccessToken(Long userId, String tokenId, String token, long expiration) {
        log.debug("无存储模式: 不存储Access Token");
    }

    @Override
    public void storeRefreshToken(Long userId, String tokenId, String token, long expiration) {
        log.debug("无存储模式: 不存储Refresh Token");
    }

    @Override
    public boolean existsAccessToken(String tokenId) {
        return true;
    }

    @Override
    public boolean existsRefreshToken(String tokenId) {
        return true;
    }

    @Override
    public void removeToken(String tokenId) {
        log.debug("无存储模式: 不移除Token");
    }

    @Override
    public void removeAllTokens(Long userId) {
        log.info("无存储模式: 不支持踢出用户");
    }

    @Override
    public int getTokenCount(Long userId) {
        return 0;
    }

    @Override
    public void refreshTokenExpiration(String tokenId, long expiration) {
        log.debug("无存储模式: 不刷新Token过期时间");
    }
}