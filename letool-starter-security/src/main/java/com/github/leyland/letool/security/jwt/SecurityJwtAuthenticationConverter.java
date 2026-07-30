package com.github.leyland.letool.security.jwt;

import com.github.leyland.letool.security.context.LoginUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 Spring Security 解码后的 {@link Jwt} 转换为 Letool 登录上下文。
 *
 * <p>角色会按照 Spring Security 约定映射为 {@code ROLE_<角色>}，
 * permissions Claim 则按原值映射为 authority。因此业务既可以继续使用
 * {@code SecurityUtil}，也可以直接使用 {@code @PreAuthorize}。</p>
 */
public final class SecurityJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /** 角色 Claim 名称。 */
    private static final String ROLES_CLAIM = "roles";

    /** 权限 Claim 名称。 */
    private static final String PERMISSIONS_CLAIM = "permissions";

    /**
     * 将已验证 JWT 转换为带 {@link LoginUser} Principal 的认证对象。
     *
     * @param jwt 已由 {@code JwtDecoder} 验证的访问令牌
     * @return 已认证对象
     * @throws OAuth2AuthenticationException 当身份或权限 Claim 类型非法时抛出
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt == null) {
            throw invalidToken("jwt");
        }
        Long userId = parseUserId(jwt.getSubject());
        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            throw invalidToken("username");
        }

        List<String> roles = readStringList(jwt, ROLES_CLAIM);
        List<String> permissions = readStringList(jwt, PERMISSIONS_CLAIM);
        LoginUser user = new LoginUser(userId, username, roles, permissions);
        String nickname = jwt.getClaimAsString("nickname");
        if (nickname != null) {
            user.setNickname(nickname);
        }

        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                toAuthorities(roles, permissions)
        );
    }

    /**
     * 将用户角色和权限转换为 Spring Security authorities。
     *
     * @param roles 用户角色
     * @param permissions 用户权限
     * @return 去重且保持声明顺序的 authorities
     */
    private Collection<? extends GrantedAuthority> toAuthorities(
            List<String> roles,
            List<String> permissions) {
        Set<String> authorityNames = new LinkedHashSet<>();
        for (String role : roles) {
            authorityNames.add(role.startsWith("ROLE_") ? role : "ROLE_" + role);
        }
        authorityNames.addAll(permissions);
        return authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /**
     * 从 JWT 读取字符串列表 Claim。
     *
     * @param jwt 已验证 JWT
     * @param claimName Claim 名称
     * @return 非空不可变字符串列表
     */
    private List<String> readStringList(Jwt jwt, String claimName) {
        Object rawValue = jwt.getClaim(claimName);
        if (rawValue == null) {
            return List.of();
        }
        if (!(rawValue instanceof Collection<?> values)) {
            throw invalidToken(claimName);
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String stringValue)
                    || stringValue.isBlank()) {
                throw invalidToken(claimName);
            }
            result.add(stringValue);
        }
        return List.copyOf(result);
    }

    /**
     * 将 subject 转换为用户 ID。
     *
     * @param subject JWT subject
     * @return 用户 ID
     */
    private Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw invalidToken("sub");
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw invalidToken("sub");
        }
    }

    /**
     * 创建不暴露 Claim 内容的标准 OAuth2 无效令牌异常。
     *
     * @param claimName 非法 Claim 名称
     * @return OAuth2 认证异常
     */
    private OAuth2AuthenticationException invalidToken(String claimName) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "JWT claim is invalid: " + claimName,
                null
        );
        return new OAuth2AuthenticationException(error);
    }
}
