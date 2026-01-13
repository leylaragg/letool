package com.github.leyland.letool.data.letool.security.jwt.jwt;

/**
 * @ClassName <h2>AuthInfo</h2>
 * @Description TODO 鉴权认证信息
 * @Author Rungo
 * @Version 1.0
 **/
public class AuthInfo {

    /**
     * 用户id
     */
    private Long id;

    /**
     * 令牌
     */
    private String accessToken;

    /**
     * 刷新token
     */
    private String refreshToken;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 过期时间
     */
    private long expiresIn;

    /**
     * 认证类型
     */
    private String tokenType;

    public AuthInfo() {

    }

    public AuthInfo(Long id, String accessToken, String refreshToken, String userName, long expiresIn, String tokenType) {
        this.id = id;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userName = userName;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    @Override
    public String toString() {
        return "AuthInfo{" +
                "id=" + id +
                ", accessToken='" + accessToken + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", userName='" + userName + '\'' +
                ", expiresIn=" + expiresIn +
                ", tokenType='" + tokenType + '\'' +
                '}';
    }
}
