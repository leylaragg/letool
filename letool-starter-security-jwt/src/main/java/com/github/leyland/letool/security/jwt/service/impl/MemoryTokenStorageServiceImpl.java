package com.github.leyland.letool.security.jwt.service.impl;

import com.github.leyland.letool.security.jwt.service.TokenStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存存储的 Token 存储服务实现
 *
 * @author Rungo
 */
public class MemoryTokenStorageServiceImpl implements TokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(MemoryTokenStorageServiceImpl.class);

    /**
     * Access Token 存储: tokenId -> TokenInfo
     */
    private final Map<String, TokenInfo> accessTokenStore = new ConcurrentHashMap<>();

    /**
     * Refresh Token 存储: tokenId -> TokenInfo
     */
    private final Map<String, TokenInfo> refreshTokenStore = new ConcurrentHashMap<>();

    /**
     * 用户Token索引: userId -> Set<tokenId>
     */
    private final Map<Long, Set<String>> userTokenIndex = new ConcurrentHashMap<>();

    @Override
    public void storeAccessToken(Long userId, String tokenId, String token, long expiration) {
        TokenInfo tokenInfo = new TokenInfo(userId, tokenId, token, expiration);
        accessTokenStore.put(tokenId, tokenInfo);
        addToUserIndex(userId, tokenId);
        log.debug("存储Access Token: userId={}, tokenId={}", userId, tokenId);
    }

    @Override
    public void storeRefreshToken(Long userId, String tokenId, String token, long expiration) {
        TokenInfo tokenInfo = new TokenInfo(userId, tokenId, token, expiration);
        refreshTokenStore.put(tokenId, tokenInfo);
        addToUserIndex(userId, tokenId);
        log.debug("存储Refresh Token: userId={}, tokenId={}", userId, tokenId);
    }

    @Override
    public boolean existsAccessToken(String tokenId) {
        TokenInfo tokenInfo = accessTokenStore.get(tokenId);
        if (tokenInfo == null) {
            return false;
        }
        // 检查是否过期
        if (tokenInfo.isExpired()) {
            accessTokenStore.remove(tokenId);
            removeFromUserIndex(tokenInfo.userId, tokenId);
            return false;
        }
        return true;
    }

    @Override
    public boolean existsRefreshToken(String tokenId) {
        TokenInfo tokenInfo = refreshTokenStore.get(tokenId);
        if (tokenInfo == null) {
            return false;
        }
        // 检查是否过期
        if (tokenInfo.isExpired()) {
            refreshTokenStore.remove(tokenId);
            removeFromUserIndex(tokenInfo.userId, tokenId);
            return false;
        }
        return true;
    }

    @Override
    public void removeToken(String tokenId) {
        TokenInfo accessInfo = accessTokenStore.remove(tokenId);
        TokenInfo refreshInfo = refreshTokenStore.remove(tokenId);

        if (accessInfo != null) {
            removeFromUserIndex(accessInfo.userId, tokenId);
        }
        if (refreshInfo != null) {
            removeFromUserIndex(refreshInfo.userId, tokenId);
        }
        log.debug("移除Token: tokenId={}", tokenId);
    }

    @Override
    public void removeAllTokens(Long userId) {
        Set<String> tokenIds = userTokenIndex.get(userId);
        if (tokenIds != null) {
            for (String tokenId : tokenIds) {
                accessTokenStore.remove(tokenId);
                refreshTokenStore.remove(tokenId);
            }
            userTokenIndex.remove(userId);
        }
        log.info("踢出用户所有Token: userId={}, count={}", userId, tokenIds != null ? tokenIds.size() : 0);
    }

    @Override
    public int getTokenCount(Long userId) {
        Set<String> tokenIds = userTokenIndex.get(userId);
        return tokenIds != null ? tokenIds.size() : 0;
    }

    @Override
    public void refreshTokenExpiration(String tokenId, long expiration) {
        TokenInfo tokenInfo = accessTokenStore.get(tokenId);
        if (tokenInfo != null) {
            tokenInfo.expiration = expiration;
            tokenInfo.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * 添加到用户Token索引
     */
    private void addToUserIndex(Long userId, String tokenId) {
        userTokenIndex.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(tokenId);
    }

    /**
     * 从用户Token索引中移除
     */
    private void removeFromUserIndex(Long userId, String tokenId) {
        Set<String> tokenIds = userTokenIndex.get(userId);
        if (tokenIds != null) {
            tokenIds.remove(tokenId);
            if (tokenIds.isEmpty()) {
                userTokenIndex.remove(userId);
            }
        }
    }

    /**
     * 清理过期Token (可定期调用)
     */
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        accessTokenStore.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                removeFromUserIndex(entry.getValue().userId, entry.getKey());
                return true;
            }
            return false;
        });
        refreshTokenStore.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                removeFromUserIndex(entry.getValue().userId, entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Token 信息内部类
     */
    private static class TokenInfo {
        final Long userId;
        final String tokenId;
        final String token;
        long expiration;
        long createdAt;

        TokenInfo(Long userId, String tokenId, String token, long expiration) {
            this.userId = userId;
            this.tokenId = tokenId;
            this.token = token;
            this.expiration = expiration;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > createdAt + expiration * 1000;
        }
    }
}