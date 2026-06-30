package com.github.leyland.letool.security.util;

import com.github.leyland.letool.security.context.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityUtil 单元测试
 */
@DisplayName("SecurityUtil 单元测试")
class SecurityUtilTest {

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        securityContextHolderMock = mockStatic(SecurityContextHolder.class);
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);

        securityContextHolderMock.when(SecurityContextHolder::getContext)
                .thenReturn(securityContext);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    @Nested
    @DisplayName("getCurrentUser 测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("已认证用户应正确返回 LoginUser")
        void shouldReturnLoginUserWhenAuthenticated() {
            LoginUser user = new LoginUser(1L, "admin",
                    Arrays.asList("ROLE_ADMIN"),
                    Arrays.asList("user:read"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            LoginUser result = SecurityUtil.getCurrentUser();

            assertNotNull(result);
            assertEquals(1L, result.getUserId());
            assertEquals("admin", result.getUsername());
        }

        @Test
        @DisplayName("未认证时应返回 null")
        void shouldReturnNullWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            LoginUser result = SecurityUtil.getCurrentUser();

            assertNull(result);
        }

        @Test
        @DisplayName("Principal 不是 LoginUser 类型时应返回 null")
        void shouldReturnNullWhenPrincipalIsNotLoginUser() {
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn("not-a-login-user");

            LoginUser result = SecurityUtil.getCurrentUser();

            assertNull(result);
        }

        @Test
        @DisplayName("Principal 为 null 时应返回 null")
        void shouldReturnNullWhenPrincipalIsNull() {
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(null);

            LoginUser result = SecurityUtil.getCurrentUser();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getCurrentUserId 测试")
    class GetCurrentUserIdTests {

        @Test
        @DisplayName("已认证用户应返回正确的 userId")
        void shouldReturnUserIdWhenAuthenticated() {
            LoginUser user = new LoginUser(42L, "user", Collections.emptyList(), Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            Long result = SecurityUtil.getCurrentUserId();

            assertEquals(42L, result);
        }

        @Test
        @DisplayName("未认证时应返回 null")
        void shouldReturnNullWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            Long result = SecurityUtil.getCurrentUserId();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getCurrentUsername 测试")
    class GetCurrentUsernameTests {

        @Test
        @DisplayName("已认证用户应返回正确的用户名")
        void shouldReturnUsernameWhenAuthenticated() {
            LoginUser user = new LoginUser(1L, "testuser", Collections.emptyList(), Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            String result = SecurityUtil.getCurrentUsername();

            assertEquals("testuser", result);
        }

        @Test
        @DisplayName("未认证时应返回 null")
        void shouldReturnNullWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            String result = SecurityUtil.getCurrentUsername();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("hasRole 测试")
    class HasRoleTests {

        @Test
        @DisplayName("用户拥有指定角色时应返回 true")
        void shouldReturnTrueWhenUserHasRole() {
            LoginUser user = new LoginUser(1L, "admin",
                    Arrays.asList("ROLE_ADMIN", "ROLE_USER"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertTrue(SecurityUtil.hasRole("ROLE_ADMIN"));
            assertTrue(SecurityUtil.hasRole("ROLE_USER"));
        }

        @Test
        @DisplayName("用户不拥有指定角色时应返回 false")
        void shouldReturnFalseWhenUserDoesNotHaveRole() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.singletonList("ROLE_USER"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasRole("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("未认证时 hasRole 应返回 false")
        void shouldReturnFalseWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertFalse(SecurityUtil.hasRole("ROLE_ADMIN"));
        }
    }

    @Nested
    @DisplayName("hasAnyRole 测试")
    class HasAnyRoleTests {

        @Test
        @DisplayName("用户拥有任一指定角色时应返回 true")
        void shouldReturnTrueWhenUserHasAnyRole() {
            LoginUser user = new LoginUser(1L, "user",
                    Arrays.asList("ROLE_USER"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertTrue(SecurityUtil.hasAnyRole("ROLE_ADMIN", "ROLE_USER"));
        }

        @Test
        @DisplayName("用户不拥有任一指定角色时应返回 false")
        void shouldReturnFalseWhenUserHasNoMatchingRole() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.singletonList("ROLE_GUEST"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasAnyRole("ROLE_ADMIN", "ROLE_USER"));
        }

        @Test
        @DisplayName("未认证时 hasAnyRole 应返回 false")
        void shouldReturnFalseWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertFalse(SecurityUtil.hasAnyRole("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("角色参数为空时应返回 false")
        void shouldReturnFalseWhenRolesEmpty() {
            LoginUser user = new LoginUser(1L, "user",
                    Arrays.asList("ROLE_USER"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasAnyRole());
        }
    }

    @Nested
    @DisplayName("hasPermission 测试")
    class HasPermissionTests {

        @Test
        @DisplayName("用户拥有指定权限时应返回 true")
        void shouldReturnTrueWhenUserHasPermission() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.emptyList(),
                    Arrays.asList("user:read", "user:write"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertTrue(SecurityUtil.hasPermission("user:read"));
            assertTrue(SecurityUtil.hasPermission("user:write"));
        }

        @Test
        @DisplayName("用户不拥有指定权限时应返回 false")
        void shouldReturnFalseWhenUserDoesNotHavePermission() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.emptyList(),
                    Collections.singletonList("user:read"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasPermission("user:delete"));
        }

        @Test
        @DisplayName("未认证时 hasPermission 应返回 false")
        void shouldReturnFalseWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertFalse(SecurityUtil.hasPermission("user:read"));
        }
    }

    @Nested
    @DisplayName("hasAnyPermission 测试")
    class HasAnyPermissionTests {

        @Test
        @DisplayName("用户拥有任一指定权限时应返回 true")
        void shouldReturnTrueWhenUserHasAnyPermission() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.emptyList(),
                    Collections.singletonList("user:read"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertTrue(SecurityUtil.hasAnyPermission("user:write", "user:read"));
        }

        @Test
        @DisplayName("用户不拥有任一指定权限时应返回 false")
        void shouldReturnFalseWhenUserHasNoMatchingPermission() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.emptyList(),
                    Collections.singletonList("user:read"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasAnyPermission("user:write", "user:delete"));
        }

        @Test
        @DisplayName("未认证时 hasAnyPermission 应返回 false")
        void shouldReturnFalseWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertFalse(SecurityUtil.hasAnyPermission("user:read"));
        }

        @Test
        @DisplayName("权限参数为空时应返回 false")
        void shouldReturnFalseWhenPermissionsEmpty() {
            LoginUser user = new LoginUser(1L, "user",
                    Collections.emptyList(),
                    Arrays.asList("user:read"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.hasAnyPermission());
        }
    }

    @Nested
    @DisplayName("getCurrentRoles 测试")
    class GetCurrentRolesTests {

        @Test
        @DisplayName("已认证用户应返回正确的角色列表")
        void shouldReturnRolesWhenAuthenticated() {
            LoginUser user = new LoginUser(1L, "admin",
                    Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER"),
                    Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            List<String> roles = SecurityUtil.getCurrentRoles();

            assertNotNull(roles);
            assertEquals(3, roles.size());
            assertTrue(roles.containsAll(Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER")));
        }

        @Test
        @DisplayName("未认证时应返回空列表")
        void shouldReturnEmptyListWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            List<String> roles = SecurityUtil.getCurrentRoles();

            assertNotNull(roles);
            assertTrue(roles.isEmpty());
        }

        @Test
        @DisplayName("用户无角色时应返回空列表")
        void shouldReturnEmptyListWhenUserHasNoRoles() {
            LoginUser user = new LoginUser(1L, "norole",
                    Collections.emptyList(), Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            List<String> roles = SecurityUtil.getCurrentRoles();

            assertNotNull(roles);
            assertTrue(roles.isEmpty());
        }
    }

    @Nested
    @DisplayName("getCurrentPermissions 测试")
    class GetCurrentPermissionsTests {

        @Test
        @DisplayName("已认证用户应返回正确的权限列表")
        void shouldReturnPermissionsWhenAuthenticated() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.emptyList(),
                    Arrays.asList("user:read", "user:write"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);

            List<String> permissions = SecurityUtil.getCurrentPermissions();

            assertNotNull(permissions);
            assertEquals(2, permissions.size());
            assertTrue(permissions.contains("user:read"));
            assertTrue(permissions.contains("user:write"));
        }

        @Test
        @DisplayName("未认证时应返回空列表")
        void shouldReturnEmptyListWhenNotAuthenticated() {
            when(securityContext.getAuthentication()).thenReturn(null);

            List<String> permissions = SecurityUtil.getCurrentPermissions();

            assertNotNull(permissions);
            assertTrue(permissions.isEmpty());
        }
    }

    @Nested
    @DisplayName("isAuthenticated 测试")
    class IsAuthenticatedTests {

        @Test
        @DisplayName("完全认证的用户应返回 true")
        void shouldReturnTrueWhenFullyAuthenticated() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.emptyList(), Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(user);

            assertTrue(SecurityUtil.isAuthenticated());
        }

        @Test
        @DisplayName("Authentication 为 null 时应返回 false")
        void shouldReturnFalseWhenAuthenticationIsNull() {
            when(securityContext.getAuthentication()).thenReturn(null);

            assertFalse(SecurityUtil.isAuthenticated());
        }

        @Test
        @DisplayName("未认证的 Authentication 应返回 false")
        void shouldReturnFalseWhenNotAuthenticated() {
            LoginUser user = new LoginUser(1L, "admin",
                    Collections.emptyList(), Collections.emptyList());
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(false);
            when(authentication.getPrincipal()).thenReturn(user);

            assertFalse(SecurityUtil.isAuthenticated());
        }

        @Test
        @DisplayName("Principal 不是 LoginUser 类型时应返回 false")
        void shouldReturnFalseWhenPrincipalIsNotLoginUser() {
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("anonymous");

            assertFalse(SecurityUtil.isAuthenticated());
        }
    }
}
