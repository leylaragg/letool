package com.github.leyland.letool.security.jwt.jwt;

/**
 * JWT Token 异常基类
 *
 * @author Rungo
 */
public class JwtTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JwtTokenException(String message) {
        super(message);
    }

    public JwtTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}