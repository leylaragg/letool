package com.github.leyland.letool.security.model;

import java.io.Serializable;

/**
 * Token 信息模型，包含 AccessToken、RefreshToken 和有效期。
 *
 * @author leyland
 * @since 2.0.0
 */
public class TokenInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** AccessToken 剩余有效时间（秒） */
    private long expiresIn;

    /** Token 类型，默认 Bearer */
    private String tokenType = "Bearer";

    public TokenInfo() {}

    /**
     * 构造 Token 信息。
     *
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌
     * @param expiresIn    AccessToken 有效期（秒）
     */
    public TokenInfo(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
}
