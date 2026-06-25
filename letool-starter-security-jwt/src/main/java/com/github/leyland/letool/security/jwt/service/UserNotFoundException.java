package com.github.leyland.letool.security.jwt.service;

/**
 * 用户不存在异常
 *
 * @author Rungo
 */
public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final Long userId;

    public UserNotFoundException(String username) {
        super("用户不存在: " + username);
        this.username = username;
        this.userId = null;
    }

    public UserNotFoundException(Long userId) {
        super("用户不存在, ID: " + userId);
        this.username = null;
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }
}