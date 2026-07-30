package com.github.leyland.letool.security.jwt;

import com.github.leyland.letool.security.config.SecurityProperties;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.security.exception.SecurityException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 令牌签发与解析器，使用 JJWT 的 HMAC-SHA256 实现。
 *
 * <p>HTTP Bearer Token 的提取、认证失败处理和过滤链执行由 Spring Security
 * Resource Server 负责。本类只保留应用登录接口所需的令牌签发，以及刷新流程
 * 所需的类型安全解析能力。</p>
 *
 * <p>Token 中存储 {@code sub}、{@code username}、{@code roles}、
 * {@code permissions} 和 {@value #TOKEN_TYPE_CLAIM}。访问令牌与刷新令牌
 * 使用不同类型声明，不能相互替代。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JwtTokenProvider {

    /** 令牌类型 Claim 名称。 */
    public static final String TOKEN_TYPE_CLAIM = "token_type";

    /** 访问令牌类型值。 */
    public static final String ACCESS_TOKEN_TYPE = "access";

    /** 刷新令牌类型值。 */
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    /** HMAC-SHA256 要求的最小密钥字节数。 */
    private static final int MINIMUM_SECRET_BYTES = 32;

    /** 历史默认密钥，仅用于识别并拒绝不安全配置。 */
    private static final String UNSAFE_DEFAULT_SECRET =
            "letool-default-secret-change-in-production";

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** JWT 签名密钥。 */
    private final SecretKey secretKey;

    /** AccessToken 有效期，单位为秒。 */
    private final long accessTokenExpiration;

    /** RefreshToken 有效期，单位为秒。 */
    private final long refreshTokenExpiration;

    /** JWT 签发者。 */
    private final String issuer;

    /** 可替换时钟，用于稳定计算签发与过期时间。 */
    private final Clock clock;

    /**
     * 使用系统 UTC 时钟初始化令牌提供器。
     *
     * @param properties 安全配置属性
     * @throws SecurityException 当 JWT 配置不满足安全要求时抛出
     */
    public JwtTokenProvider(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * 使用指定时钟初始化令牌提供器。
     *
     * <p>该构造器便于测试稳定控制时间，也允许特殊运行环境提供统一时钟。</p>
     *
     * @param properties 安全配置属性
     * @param clock 令牌时间来源
     * @throws SecurityException 当 JWT 配置或时钟不合法时抛出
     */
    public JwtTokenProvider(SecurityProperties properties, Clock clock) {
        SecurityProperties.Jwt jwt = requireJwtProperties(properties);
        this.secretKey = createSecretKey(jwt);
        this.accessTokenExpiration = requirePositive(
                jwt.getAccessTokenExpiration(),
                "jwt.accessTokenExpiration"
        );
        this.refreshTokenExpiration = requirePositive(
                jwt.getRefreshTokenExpiration(),
                "jwt.refreshTokenExpiration"
        );
        if (jwt.getIssuer() == null || jwt.getIssuer().isBlank()) {
            throw SecurityException.configurationInvalid("jwt.issuer");
        }
        if (clock == null) {
            throw SecurityException.configurationInvalid("clock");
        }
        this.issuer = jwt.getIssuer().trim();
        this.clock = clock;
    }

    /**
     * 根据 JWT 配置创建符合 HMAC-SHA256 强度要求的密钥。
     *
     * <p>该方法供 Spring Security {@code JwtDecoder} 与令牌签发器共享同一套
     * 安全校验规则。方法不会记录或返回原始密钥文本。</p>
     *
     * @param jwt JWT 配置
     * @return HMAC-SHA256 密钥
     * @throws SecurityException 当密钥为空、仍为历史默认值或不足 256 位时抛出
     */
    public static SecretKey createSecretKey(SecurityProperties.Jwt jwt) {
        if (jwt == null) {
            throw SecurityException.configurationInvalid("jwt");
        }
        String secret = jwt.getSecret();
        if (secret == null
                || secret.isBlank()
                || UNSAFE_DEFAULT_SECRET.equals(secret)) {
            throw SecurityException.configurationInvalid("jwt.secret");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw SecurityException.configurationInvalid("jwt.secret");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌。
     *
     * @param user 登录用户信息
     * @return 带 {@code token_type=access} 的 JWT
     * @throws SecurityException 当用户身份不完整时抛出
     */
    public String generateAccessToken(LoginUser user) {
        return generateToken(user, accessTokenExpiration, ACCESS_TOKEN_TYPE);
    }

    /**
     * 生成刷新令牌。
     *
     * @param user 登录用户信息
     * @return 带 {@code token_type=refresh} 的 JWT
     * @throws SecurityException 当用户身份不完整时抛出
     */
    public String generateRefreshToken(LoginUser user) {
        return generateToken(user, refreshTokenExpiration, REFRESH_TOKEN_TYPE);
    }

    /**
     * 解析访问令牌为登录用户。
     *
     * <p>为保持原 API 兼容，该方法名称不带 Access；刷新令牌必须使用
     * {@link #parseRefreshToken(String)}。</p>
     *
     * @param token JWT 字符串
     * @return 登录用户；令牌无效、过期或类型不是 access 时返回 {@code null}
     */
    public LoginUser parseToken(String token) {
        return parseUser(token, ACCESS_TOKEN_TYPE);
    }

    /**
     * 解析刷新令牌为登录用户。
     *
     * @param token JWT 字符串
     * @return 登录用户；令牌无效、过期或类型不是 refresh 时返回 {@code null}
     */
    public LoginUser parseRefreshToken(String token) {
        return parseUser(token, REFRESH_TOKEN_TYPE);
    }

    /**
     * 验证访问令牌。
     *
     * @param token JWT 字符串
     * @return 签名、签发者、有效期和令牌类型均正确时返回 {@code true}
     */
    public boolean validateToken(String token) {
        return parseClaims(token, ACCESS_TOKEN_TYPE) != null;
    }

    /**
     * 验证刷新令牌。
     *
     * @param token JWT 字符串
     * @return 签名、签发者、有效期和令牌类型均正确时返回 {@code true}
     */
    public boolean validateRefreshToken(String token) {
        return parseClaims(token, REFRESH_TOKEN_TYPE) != null;
    }

    /**
     * 获取 AccessToken 有效期。
     *
     * @return 有效期秒数
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /**
     * 获取 RefreshToken 有效期。
     *
     * @return 有效期秒数
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * 生成指定类型的 JWT。
     *
     * @param user 登录用户
     * @param expirationSeconds 有效期秒数
     * @param tokenType 令牌类型
     * @return 已签名 JWT
     */
    private String generateToken(
            LoginUser user,
            long expirationSeconds,
            String tokenType) {
        validateUser(user);
        Instant issuedAt = clock.instant();
        Instant expiresAt;
        Date issuedAtDate;
        Date expiresAtDate;
        try {
            expiresAt = issuedAt.plusSeconds(expirationSeconds);
            issuedAtDate = Date.from(issuedAt);
            expiresAtDate = Date.from(expiresAt);
        } catch (DateTimeException | IllegalArgumentException
                 | ArithmeticException exception) {
            throw SecurityException.configurationInvalid("jwt.expiration");
        }

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(issuedAtDate)
                .expiration(expiresAtDate);
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            builder.claim("nickname", user.getNickname());
        }
        return builder.signWith(secretKey).compact();
    }

    /**
     * 解析并转换指定类型令牌。
     *
     * @param token JWT 字符串
     * @param expectedType 期望令牌类型
     * @return 登录用户；解析或 Claim 校验失败时返回 {@code null}
     */
    private LoginUser parseUser(String token, String expectedType) {
        Claims claims = parseClaims(token, expectedType);
        if (claims == null) {
            return null;
        }
        String subject = claims.getSubject();
        String username = claims.get("username", String.class);
        if (subject == null || subject.isBlank()
                || username == null || username.isBlank()) {
            return null;
        }

        Long userId;
        try {
            userId = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            log.debug("JWT subject is not a numeric user id");
            return null;
        }

        List<String> roles = readStringList(claims, "roles");
        List<String> permissions = readStringList(claims, "permissions");
        if (roles == null || permissions == null) {
            return null;
        }

        LoginUser user = new LoginUser(userId, username, roles, permissions);
        String nickname = claims.get("nickname", String.class);
        if (nickname != null) {
            user.setNickname(nickname);
        }
        return user;
    }

    /**
     * 解析并校验 JWT Claims。
     *
     * @param token JWT 字符串
     * @param expectedType 期望令牌类型
     * @return Claims；任何校验失败时返回 {@code null}
     */
    private Claims parseClaims(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .require(TOKEN_TYPE_CLAIM, expectedType)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            log.debug("JWT token expired");
            return null;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("JWT token validation failed: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 安全读取字符串集合 Claim。
     *
     * @param claims JWT Claims
     * @param claimName Claim 名称
     * @return 不可变字符串列表；类型非法时返回 {@code null}
     */
    private List<String> readStringList(Claims claims, String claimName) {
        Object rawValue = claims.get(claimName);
        if (rawValue == null) {
            return Collections.emptyList();
        }
        if (!(rawValue instanceof Collection<?> values)) {
            return null;
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String stringValue)
                    || stringValue.isBlank()) {
                return null;
            }
            result.add(stringValue);
        }
        return List.copyOf(result);
    }

    /**
     * 校验登录用户签发所需的最小身份字段。
     *
     * @param user 登录用户
     * @throws SecurityException 当用户或身份字段不完整时抛出
     */
    private void validateUser(LoginUser user) {
        if (user == null) {
            throw SecurityException.configurationInvalid("user");
        }
        if (user.getUserId() == null) {
            throw SecurityException.configurationInvalid("user.userId");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw SecurityException.configurationInvalid("user.username");
        }
    }

    /**
     * 获取并校验 JWT 聚合配置。
     *
     * @param properties 安全配置
     * @return JWT 配置
     */
    private static SecurityProperties.Jwt requireJwtProperties(
            SecurityProperties properties) {
        if (properties == null || properties.getJwt() == null) {
            throw SecurityException.configurationInvalid("jwt");
        }
        return properties.getJwt();
    }

    /**
     * 校验正数配置。
     *
     * @param value 配置值
     * @param field 配置字段名
     * @return 已校验配置值
     */
    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw SecurityException.configurationInvalid(field);
        }
        return value;
    }
}
