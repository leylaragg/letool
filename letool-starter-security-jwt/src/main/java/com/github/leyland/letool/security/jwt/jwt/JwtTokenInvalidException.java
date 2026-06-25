package com.github.leyland.letool.security.jwt.jwt;

/**
 * JWT Token 无效异常
 *
 * @author Rungo
 */
public class JwtTokenInvalidException extends JwtTokenException {

    private static final long serialVersionUID = 1L;

    public JwtTokenInvalidException(String message) {
        super(message);
    }

    public JwtTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}