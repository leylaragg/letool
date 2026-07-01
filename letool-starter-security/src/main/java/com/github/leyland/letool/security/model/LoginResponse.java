package com.github.leyland.letool.security.model;

import java.io.Serializable;

/**
 * 登录响应模型，包含 Token 信息和用户基本数据。
 *
 * @author leyland
 * @since 2.0.0
 */
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Token 信息（AccessToken / RefreshToken / 有效期） */
    private TokenInfo tokenInfo;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 登录时间戳（毫秒） */
    private long loginTime;

    public LoginResponse() {}

    /**
     * 构造登录响应，自动记录当前时间戳。
     *
     * @param tokenInfo Token 信息
     * @param userId    用户 ID
     * @param username  用户名
     */
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
