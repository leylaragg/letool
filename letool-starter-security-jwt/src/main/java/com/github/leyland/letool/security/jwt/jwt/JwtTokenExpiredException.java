package com.github.leyland.letool.security.jwt.jwt;

/**
 * JWT Token 过期异常
 *
 * @author Rungo
 */
public class JwtTokenExpiredException extends JwtTokenException {

    private static final long serialVersionUID = 1L;

    public JwtTokenExpiredException(String message) {
        super(message);
    }

    public JwtTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}