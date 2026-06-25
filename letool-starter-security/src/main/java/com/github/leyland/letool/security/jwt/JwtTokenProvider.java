package com.github.leyland.letool.security.jwt;

import com.github.leyland.letool.security.config.SecurityProperties;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.tool.util.JsonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    public JwtTokenProvider(SecurityProperties properties) {
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.accessTokenExpiration = properties.getJwt().getAccessTokenExpiration();
        this.refreshTokenExpiration = properties.getJwt().getRefreshTokenExpiration();
        this.issuer = properties.getJwt().getIssuer();
    }

    public String generateAccessToken(LoginUser user) {
        return generateToken(user, accessTokenExpiration);
    }

    public String generateRefreshToken(LoginUser user) {
        return generateToken(user, refreshTokenExpiration);
    }

    private String generateToken(LoginUser user, long expirationSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        LoginUser user = new LoginUser(userId, username, roles, permissions);
        if (claims.get("nickname") != null) {
            user.setNickname(claims.get("nickname", String.class));
        }
        return user;
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token parse error: {}", e.getMessage());
            return null;
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
