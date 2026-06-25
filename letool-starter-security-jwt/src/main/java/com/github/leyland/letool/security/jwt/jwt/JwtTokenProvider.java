package com.github.leyland.letool.security.jwt.jwt;

import com.github.leyland.letool.security.jwt.constant.SecurityJwtConstant;
import com.github.leyland.letool.security.jwt.config.SecurityJwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JWT Token 生成与解析工具
 *
 * @author Rungo
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecurityJwtProperties.JwtConfig jwtConfig;
    private final SecretKey secretKey;

    public JwtTokenProvider(SecurityJwtProperties.JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token
     *
     * @param jwtUser 用户信息
     * @return Access Token
     */
    public String generateAccessToken(JwtUser jwtUser) {
        return generateToken(jwtUser, SecurityJwtConstant.TOKEN_TYPE_ACCESS, jwtConfig.getAccessTokenExpiration());
    }

    /**
     * 生成 Refresh Token
     *
     * @param jwtUser 用户信息
     * @return Refresh Token
     */
    public String generateRefreshToken(JwtUser jwtUser) {
        return generateToken(jwtUser, SecurityJwtConstant.TOKEN_TYPE_REFRESH, jwtConfig.getRefreshTokenExpiration());
    }

    /**
     * 生成完整认证信息 (Access Token + Refresh Token)
     *
     * @param jwtUser 用户信息
     * @return AuthInfo
     */
    public AuthInfo generateAuthInfo(JwtUser jwtUser) {
        String accessToken = generateAccessToken(jwtUser);
        String refreshToken = generateRefreshToken(jwtUser);

        AuthInfo authInfo = new AuthInfo();
        authInfo.setId(jwtUser.getId());
        authInfo.setAccessToken(accessToken);
        authInfo.setRefreshToken(refreshToken);
        authInfo.setUserName(jwtUser.getUsername());
        authInfo.setExpiresIn(jwtConfig.getAccessTokenExpiration());
        authInfo.setTokenType(jwtConfig.getTokenPrefix().trim());

        return authInfo;
    }

    /**
     * 生成 Token
     *
     * @param jwtUser    用户信息
     * @param tokenType  Token类型
     * @param expiration 过期时间(秒)
     * @return Token字符串
     */
    private String generateToken(JwtUser jwtUser, String tokenType, long expiration) {
        long now = System.currentTimeMillis();
        String tokenId = UUID.randomUUID().toString().replace("-", "");

        JwtBuilder builder = Jwts.builder()
                .setId(tokenId)
                .setSubject(jwtUser.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiration * 1000))
                .claim(SecurityJwtConstant.CLAIM_USER_ID, jwtUser.getId())
                .claim(SecurityJwtConstant.CLAIM_TOKEN_TYPE, tokenType)
                .claim(SecurityJwtConstant.CLAIM_TOKEN_ID, tokenId)
                .signWith(secretKey, SignatureAlgorithm.HS256);

        // 添加角色
        if (jwtUser.getRoles() != null && !jwtUser.getRoles().isEmpty()) {
            builder.claim(SecurityJwtConstant.CLAIM_ROLES, jwtUser.getRoles());
        }

        // 添加权限
        if (jwtUser.getPermissions() != null && !jwtUser.getPermissions().isEmpty()) {
            builder.claim(SecurityJwtConstant.CLAIM_PERMISSIONS, jwtUser.getPermissions());
        }

        // 添加状态
        if (jwtUser.getStatus() != null) {
            builder.claim(SecurityJwtConstant.CLAIM_STATUS, jwtUser.getStatus());
        }

        // 添加附加信息
        if (jwtUser.getExtra() != null && !jwtUser.getExtra().isEmpty()) {
            for (Map.Entry<String, Object> entry : jwtUser.getExtra().entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }
        }

        return builder.compact();
    }

    /**
     * 解析 Token
     *
     * @param token Token字符串
     * @return TokenPayload
     */
    public TokenPayload parseToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            TokenPayload payload = new TokenPayload();
            payload.setUserId(claims.get(SecurityJwtConstant.CLAIM_USER_ID, Long.class));
            payload.setUsername(claims.getSubject());
            payload.setTokenId(claims.get(SecurityJwtConstant.CLAIM_TOKEN_ID, String.class));
            payload.setTokenType(claims.get(SecurityJwtConstant.CLAIM_TOKEN_TYPE, String.class));
            payload.setIssuer(claims.getIssuer());
            payload.setIssuedAt(claims.getIssuedAt().getTime());
            payload.setExpiresAt(claims.getExpiration().getTime());

            // 解析角色
            List<String> roles = claims.get(SecurityJwtConstant.CLAIM_ROLES, List.class);
            if (roles != null) {
                payload.setRoles(roles.toArray(new String[0]));
            }

            // 解析权限
            List<String> permissions = claims.get(SecurityJwtConstant.CLAIM_PERMISSIONS, List.class);
            if (permissions != null) {
                payload.setPermissions(permissions.toArray(new String[0]));
            }

            // 存储所有 claims
            Map<String, Object> extraClaims = new HashMap<>();
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                String key = entry.getKey();
                if (!key.equals(SecurityJwtConstant.CLAIM_USER_ID)
                        && !key.equals(SecurityJwtConstant.CLAIM_USERNAME)
                        && !key.equals(SecurityJwtConstant.CLAIM_ROLES)
                        && !key.equals(SecurityJwtConstant.CLAIM_PERMISSIONS)
                        && !key.equals(SecurityJwtConstant.CLAIM_TOKEN_ID)
                        && !key.equals(SecurityJwtConstant.CLAIM_TOKEN_TYPE)) {
                    extraClaims.put(key, entry.getValue());
                }
            }
            payload.setClaims(extraClaims);

            return payload;
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new JwtTokenExpiredException("Token已过期", e);
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token: {}", e.getMessage());
            throw new JwtTokenInvalidException("不支持的Token格式", e);
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token格式错误", e);
        } catch (SecurityException e) {
            log.warn("Token签名验证失败: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token签名验证失败", e);
        } catch (IllegalArgumentException e) {
            log.warn("Token参数非法: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token参数非法", e);
        }
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token Token字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Token 是否过期
     *
     * @param token Token字符串
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 从请求头中提取 Token
     *
     * @param authHeader Authorization请求头值
     * @return Token字符串 (不含前缀)
     */
    public String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        String prefix = jwtConfig.getTokenPrefix();
        if (authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length());
        }
        return authHeader;
    }

    /**
     * 获取 Token 过期时间 (秒)
     *
     * @return 过期时间
     */
    public long getAccessTokenExpiration() {
        return jwtConfig.getAccessTokenExpiration();
    }

    /**
     * 获取 Refresh Token 过期时间 (秒)
     *
     * @return 过期时间
     */
    public long getRefreshTokenExpiration() {
        return jwtConfig.getRefreshTokenExpiration();
    }

    /**
     * 获取请求头名称
     *
     * @return 请求头名称
     */
    public String getHeaderName() {
        return jwtConfig.getHeaderName();
    }

    /**
     * 获取 Token 前缀
     *
     * @return Token前缀
     */
    public String getTokenPrefix() {
        return jwtConfig.getTokenPrefix();
    }
}