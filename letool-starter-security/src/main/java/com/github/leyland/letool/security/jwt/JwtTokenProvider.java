package com.github.leyland.letool.security.jwt;

import com.github.leyland.letool.security.config.SecurityProperties;
import com.github.leyland.letool.security.context.LoginUser;
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

/**
 * JWT 令牌生成与解析器，使用 HMAC-SHA256 签名算法。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>生成 AccessToken / RefreshToken</li>
 *   <li>解析 Token 还原为 {@link LoginUser}</li>
 *   <li>验证 Token 签名和有效期</li>
 * </ul>
 *
 * <p>Token 中存储的 Claims：sub（用户ID）、username、roles、permissions。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    /**
     * 从配置属性初始化密钥和有效期。
     *
     * @param properties 安全配置属性
     */
    public JwtTokenProvider(SecurityProperties properties) {
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.accessTokenExpiration = properties.getJwt().getAccessTokenExpiration();
        this.refreshTokenExpiration = properties.getJwt().getRefreshTokenExpiration();
        this.issuer = properties.getJwt().getIssuer();

        if ("letool-default-secret-change-in-production".equals(properties.getJwt().getSecret())) {
            log.warn("JWT secret is using the default value — configure letool.security.jwt.secret for production use");
        }
    }

    /**
     * 生成 AccessToken，有效期由 {@code letool.security.jwt.access-token-expiration} 控制。
     *
     * @param user 登录用户信息
     * @return JWT 签名字符串
     */
    public String generateAccessToken(LoginUser user) {
        return generateToken(user, accessTokenExpiration);
    }

    /**
     * 生成 RefreshToken，有效期由 {@code letool.security.jwt.refresh-token-expiration} 控制。
     *
     * @param user 登录用户信息
     * @return JWT 签名字符串
     */
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

    /**
     * 解析 Token 为 {@link LoginUser}。
     *
     * @param token JWT 签名字符串
     * @return 解析出的用户信息，Token 无效或过期时返回 {@code null}
     */
    public LoginUser parseToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;

        // 防御性解析 sub 字段：恶意或非法的 JWT 可能包含空或非数字的 sub 值
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) return null;
        Long userId;
        try {
            userId = Long.valueOf(sub);
        } catch (NumberFormatException e) {
            log.debug("Invalid JWT subject (not a valid userId): {}", sub);
            return null;
        }
        String username = claims.get("username", String.class);
        // JJWT stores List claims as raw List — unchecked cast is unavoidable
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        LoginUser user = new LoginUser(userId, username, roles, permissions);
        String nickname = claims.get("nickname", String.class);
        if (nickname != null) {
            user.setNickname(nickname);
        }
        return user;
    }

    /**
     * 验证 Token 签名和有效期是否有效。
     *
     * @param token JWT 签名字符串
     * @return {@code true} 如果 Token 签名正确且未过期
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Token 的 Claims，不区分过期和其他错误。
     *
     * <p>调用方如需区分过期 Token，可以直接 catch {@link ExpiredJwtException}。</p>
     *
     * @param token JWT 签名字符串
     * @return Claims 对象，解析失败或过期返回 {@code null}
     */
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

    /** @return AccessToken 有效期（秒） */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /** @return RefreshToken 有效期（秒） */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
