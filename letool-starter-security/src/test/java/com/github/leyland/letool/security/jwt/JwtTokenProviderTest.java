package com.github.leyland.letool.security.jwt;

import com.github.leyland.letool.security.config.SecurityProperties;
import com.github.leyland.letool.security.context.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 */
@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    private SecurityProperties properties;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.getJwt().setAccessTokenExpiration(300);
        properties.getJwt().setRefreshTokenExpiration(3600);
        properties.getJwt().setSecret("test-secret-key-for-jwt-signing-256bits!!");
        properties.getJwt().setIssuer("test-issuer");
        provider = new JwtTokenProvider(properties);
    }

    @Nested
    @DisplayName("令牌生成测试")
    class TokenGenerationTests {

        @Test
        @DisplayName("应该成功生成 accessToken")
        void shouldGenerateAccessToken() {
            LoginUser user = new LoginUser(1L, "admin",
                    Arrays.asList("ROLE_ADMIN", "ROLE_USER"),
                    Arrays.asList("user:read", "user:write"));

            String token = provider.generateAccessToken(user);

            assertNotNull(token);
            assertFalse(token.isEmpty());
            // JWT 应该包含 3 段，以 . 分隔
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length);
        }

        @Test
        @DisplayName("应该成功生成 refreshToken")
        void shouldGenerateRefreshToken() {
            LoginUser user = new LoginUser(2L, "user",
                    Collections.singletonList("ROLE_USER"),
                    Collections.singletonList("user:read"));

            String token = provider.generateRefreshToken(user);

            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        @DisplayName("accessToken 与 refreshToken 应该不同")
        void accessTokenAndRefreshTokenShouldDiffer() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());

            String accessToken = provider.generateAccessToken(user);
            String refreshToken = provider.generateRefreshToken(user);

            assertNotEquals(accessToken, refreshToken);
        }

        @Test
        @DisplayName("不同用户的令牌应该不同")
        void tokensShouldDifferForDifferentUsers() {
            LoginUser user1 = new LoginUser(1L, "admin", Collections.emptyList(), Collections.emptyList());
            LoginUser user2 = new LoginUser(2L, "user", Collections.emptyList(), Collections.emptyList());

            String token1 = provider.generateAccessToken(user1);
            String token2 = provider.generateAccessToken(user2);

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("空角色和权限的令牌应该正常生成")
        void shouldGenerateTokenWithEmptyRolesAndPermissions() {
            LoginUser user = new LoginUser(3L, "guest", null, null);

            String token = provider.generateAccessToken(user);

            assertNotNull(token);
            assertTrue(provider.validateToken(token));
        }
    }

    @Nested
    @DisplayName("令牌解析测试")
    class TokenParsingTests {

        @Test
        @DisplayName("应该正确解析有效令牌中的用户信息")
        void shouldParseValidToken() {
            LoginUser original = new LoginUser(100L, "testuser",
                    Arrays.asList("ROLE_USER"),
                    Arrays.asList("read", "write"));
            original.setNickname("Test Nickname");

            String token = provider.generateAccessToken(original);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertEquals(100L, parsed.getUserId());
            assertEquals("testuser", parsed.getUsername());
            assertTrue(parsed.getRoles().contains("ROLE_USER"));
            assertTrue(parsed.getPermissions().contains("read"));
            assertTrue(parsed.getPermissions().contains("write"));
        }

        @Test
        @DisplayName("解析时应该保留角色列表的完整性")
        void shouldParseAllRoles() {
            LoginUser original = new LoginUser(1L, "multiRole",
                    Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER"),
                    Collections.emptyList());

            String token = provider.generateAccessToken(original);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertEquals(3, parsed.getRoles().size());
            assertTrue(parsed.getRoles().containsAll(
                    Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER")));
        }

        @Test
        @DisplayName("解析时应该保留权限列表的完整性")
        void shouldParseAllPermissions() {
            LoginUser original = new LoginUser(1L, "user",
                    Collections.emptyList(),
                    Arrays.asList("user:read", "user:write", "user:delete", "admin:read"));

            String token = provider.generateAccessToken(original);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertEquals(4, parsed.getPermissions().size());
            assertTrue(parsed.getPermissions().containsAll(
                    Arrays.asList("user:read", "user:write", "user:delete", "admin:read")));
        }

        @Test
        @DisplayName("应该能够解析没有 nickname 的用户令牌")
        void shouldParseTokenWithoutNickname() {
            LoginUser original = new LoginUser(5L, "nonick",
                    Collections.singletonList("ROLE_USER"),
                    Collections.emptyList());
            // 不设置 nickname

            String token = provider.generateAccessToken(original);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertEquals(5L, parsed.getUserId());
            assertEquals("nonick", parsed.getUsername());
            assertNull(parsed.getNickname());
        }

        @Test
        @DisplayName("解析恶意/无效令牌应返回 null")
        void shouldReturnNullForMalformedToken() {
            LoginUser result = provider.parseToken("not-a-valid-jwt-token-at-all");
            assertNull(result);
        }

        @Test
        @DisplayName("解析空字符串令牌应返回 null")
        void shouldReturnNullForEmptyToken() {
            LoginUser result = provider.parseToken("");
            assertNull(result);
        }

        @Test
        @DisplayName("解析 null 令牌应返回 null")
        void shouldReturnNullForNullToken() {
            // 注意：parseToken 调用 parseClaims，Jwts.parser() 在 token 为 null 时会抛异常
            // 被 catch 后返回 null
            LoginUser result = provider.parseToken(null);
            assertNull(result);
        }

        @Test
        @DisplayName("解析被篡改的令牌应返回 null")
        void shouldReturnNullForTamperedToken() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());
            String token = provider.generateAccessToken(user);
            // 篡改令牌的 payload 部分
            String tamperedToken = token.substring(0, token.length() - 3) + "xyz";

            LoginUser result = provider.parseToken(tamperedToken);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("令牌验证测试（parseToken 验证法）")
    class TokenValidationTests {

        @Test
        @DisplayName("有效令牌应能通过 parseToken 成功解析")
        void validTokenShouldParseSuccessfully() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());

            String token = provider.generateAccessToken(user);
            LoginUser result = provider.parseToken(token);

            assertNotNull(result);
            assertEquals(1L, result.getUserId());
        }

        @Test
        @DisplayName("无效令牌通过 parseToken 解析应返回 null")
        void invalidTokenParseShouldReturnNull() {
            LoginUser result = provider.parseToken("invalid-token");
            assertNull(result, "无效令牌解析结果应为 null");
        }

        @Test
        @DisplayName("空字符串令牌通过 parseToken 解析应返回 null")
        void emptyTokenParseShouldReturnNull() {
            LoginUser result = provider.parseToken("");
            assertNull(result, "空字符串令牌解析结果应为 null");
        }

        @Test
        @DisplayName("null 令牌通过 parseToken 解析应返回 null")
        void nullTokenParseShouldReturnNull() {
            // parseClaims 内部 catch 了 IllegalArgumentException
            LoginUser result = provider.parseToken(null);
            assertNull(result, "null 令牌解析结果应为 null");
        }

        @Test
        @DisplayName("已过期的令牌通过 parseToken 解析应返回 null")
        void expiredTokenParseShouldReturnNull() throws InterruptedException {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());

            // 使用 1 秒过期时间生成令牌
            properties.getJwt().setAccessTokenExpiration(1);
            JwtTokenProvider shortLived = new JwtTokenProvider(properties);
            String token = shortLived.generateAccessToken(user);
            // 等待令牌过期
            Thread.sleep(1500);

            LoginUser result = shortLived.parseToken(token);
            assertNull(result, "已过期的令牌解析结果应为 null");
        }

        @Test
        @DisplayName("不同密钥签名的令牌通过 parseToken 解析应返回 null")
        void differentKeyTokenParseShouldReturnNull() {
            SecurityProperties otherProps = new SecurityProperties();
            otherProps.getJwt().setSecret("completely-different-secret-key-here!!");
            otherProps.getJwt().setIssuer("test-issuer");
            JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);

            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());
            String token = otherProvider.generateAccessToken(user);

            LoginUser result = provider.parseToken(token);
            assertNull(result, "不同密钥签名的令牌解析结果应为 null");
        }

        @Test
        @DisplayName("不同 issuer 的令牌通过 parseToken 解析应返回 null")
        void differentIssuerTokenParseShouldReturnNull() {
            SecurityProperties otherProps = new SecurityProperties();
            otherProps.getJwt().setSecret("test-secret-key-for-jwt-signing-256bits!!");
            otherProps.getJwt().setIssuer("different-issuer");
            JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);

            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());
            String token = otherProvider.generateAccessToken(user);

            LoginUser result = provider.parseToken(token);
            assertNull(result, "issuer 不匹配的令牌解析结果应为 null");
        }

        @Test
        @DisplayName("篡改后的令牌通过 parseToken 解析应返回 null")
        void tamperedTokenParseShouldReturnNull() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.singletonList("ROLE_ADMIN"),
                    Collections.emptyList());
            String token = provider.generateAccessToken(user);
            // 修改令牌中的一个字符
            char[] chars = token.toCharArray();
            chars[chars.length / 2] = (char) (chars[chars.length / 2] + 1);
            String tamperedToken = new String(chars);

            LoginUser result = provider.parseToken(tamperedToken);
            assertNull(result, "篡改后的令牌解析结果应为 null");
        }
    }

    @Nested
    @DisplayName("令牌过期时间测试")
    class ExpirationTests {

        @Test
        @DisplayName("应该正确返回 accessToken 过期时间")
        void shouldReturnAccessTokenExpiration() {
            assertEquals(300, provider.getAccessTokenExpiration());
        }

        @Test
        @DisplayName("应该正确返回 refreshToken 过期时间")
        void shouldReturnRefreshTokenExpiration() {
            assertEquals(3600, provider.getRefreshTokenExpiration());
        }

        @Test
        @DisplayName("refreshToken 过期时间应该长于 accessToken")
        void refreshTokenExpirationShouldBeLonger() {
            assertTrue(provider.getRefreshTokenExpiration() > provider.getAccessTokenExpiration());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("用户角色为空列表时生成的令牌应可正常解析")
        void shouldHandleEmptyRoles() {
            LoginUser user = new LoginUser(1L, "user", Collections.emptyList(),
                    Collections.singletonList("read"));

            String token = provider.generateAccessToken(user);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertTrue(parsed.getRoles().isEmpty());
            assertFalse(parsed.getPermissions().isEmpty());
        }

        @Test
        @DisplayName("用户权限为空列表时生成的令牌应可正常解析")
        void shouldHandleEmptyPermissions() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.singletonList("ROLE_USER"),
                    Collections.emptyList());

            String token = provider.generateAccessToken(user);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertFalse(parsed.getRoles().isEmpty());
            assertTrue(parsed.getPermissions().isEmpty());
        }

        @Test
        @DisplayName("用户 ID 为较大值时应正常生成令牌")
        void shouldHandleLargeUserId() {
            long largeUserId = 9999999999999L;
            LoginUser user = new LoginUser(largeUserId, "biguser",
                    Collections.emptyList(), Collections.emptyList());

            String token = provider.generateAccessToken(user);
            LoginUser parsed = provider.parseToken(token);

            assertNotNull(parsed);
            assertEquals(largeUserId, parsed.getUserId());
        }

        @Test
        @DisplayName("用户名包含特殊字符时应正常生成令牌")
        void shouldHandleSpecialCharactersInUsername() {
            LoginUser user = new LoginUser(1L, "user@domain.com",
                    Collections.singletonList("ROLE_USER"),
                    Collections.emptyList());

            String token = provider.generateAccessToken(user);
            assertNotNull(token);
            assertTrue(provider.validateToken(token));

            LoginUser parsed = provider.parseToken(token);
            assertEquals("user@domain.com", parsed.getUsername());
        }

        @Test
        @DisplayName("不同用户的令牌应不同")
        void tokensForDifferentUsersShouldDiffer() {
            LoginUser user1 = new LoginUser(1L, "admin", Collections.emptyList(), Collections.emptyList());
            LoginUser user2 = new LoginUser(2L, "user", Collections.emptyList(), Collections.emptyList());

            String token1 = provider.generateAccessToken(user1);
            String token2 = provider.generateAccessToken(user2);

            assertNotEquals(token1, token2,
                    "不同用户的令牌应不同（subject 不同导致签名不同）");
        }
    }
}
