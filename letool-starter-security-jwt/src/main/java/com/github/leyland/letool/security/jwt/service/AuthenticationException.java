package com.github.leyland.letool.security.jwt.service;

/**
 * 认证异常
 *
 * @author Rungo
 */
public class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}