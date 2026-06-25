package com.github.leyland.letool.security.jwt.jwt;

import java.io.Serializable;
import java.util.Map;

/**
 * Token 刷新请求
 *
 * @author Rungo
 */
public class RefreshTokenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}