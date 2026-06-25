package com.github.leyland.letool.security.model;

import java.io.Serializable;

public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private TokenInfo tokenInfo;
    private Long userId;
    private String username;
    private String nickname;
    private long loginTime;

    public LoginResponse() {}

    public LoginResponse(TokenInfo tokenInfo, Long userId, String username) {
        this.tokenInfo = tokenInfo;
        this.userId = userId;
        this.username = username;
        this.loginTime = System.currentTimeMillis();
    }

    public TokenInfo getTokenInfo() { return tokenInfo; }
    public void setTokenInfo(TokenInfo tokenInfo) { this.tokenInfo = tokenInfo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public long getLoginTime() { return loginTime; }
    public void setLoginTime(long loginTime) { this.loginTime = loginTime; }
}
