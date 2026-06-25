package com.github.leyland.letool.security.jwt.service;

/**
 * Token 存储服务接口
 * <p>
 * 用于存储和管理已生成的 Token，支持 Token 校验和用户踢出功能
 *
 * @author Rungo
 */
public interface TokenStorageService {

    /**
     * 存储 Access Token
     *
     * @param userId  用户ID
     * @param tokenId Token唯一标识
     * @param token   Token字符串
     * @param expiration 过期时间(秒)
     */
    void storeAccessToken(Long userId, String tokenId, String token, long expiration);

    /**
     * 存储 Refresh Token
     *
     * @param userId  用户ID
     * @param tokenId Token唯一标识
     * * @param token   Token字符串
     * @param expiration 过期时间(秒)
     */
    void storeRefreshToken(Long userId, String tokenId, String token, long expiration);

    /**
     * 检查 Access Token 是否存在于存储中
     *
     * @param tokenId Token唯一标识
     * @return 是否存在
     */
    boolean existsAccessToken(String tokenId);

    /**
     * 检查 Refresh Token 是否存在于存储中
     *
     * @param tokenId Token唯一标识
     * @return 是否存在
     */
    boolean existsRefreshToken(String tokenId);

    /**
     * 移除指定 Token
     *
     * @param tokenId Token唯一标识
     */
    void removeToken(String tokenId);

    /**
     * 移除用户的所有 Token (踢出用户)
     *
     * @param userId 用户ID
     */
    void removeAllTokens(Long userId);

    /**
     * 获取用户的活跃 Token 数量
     *
     * @param userId 用户ID
     * @return Token数量
     */
    int getTokenCount(Long userId);

    /**
     * 刷新 Token 过期时间
     *
     * @param tokenId Token唯一标识
     * @param expiration 新的过期时间(秒)
     */
    void refreshTokenExpiration(String tokenId, long expiration);
}