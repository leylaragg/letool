package com.github.leyland.letool.websocket.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WsPrincipal WebSocket 用户主体测试")
class WsPrincipalTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("三参数构造应正确初始化所有字段")
        void threeArgConstructorShouldInitializeAllFields() {
            List<String> roles = Arrays.asList("admin", "user");
            WsPrincipal principal = new WsPrincipal("u001", "Alice", roles);

            assertEquals("u001", principal.getUserId());
            assertEquals("Alice", principal.getUsername());
            assertEquals(2, principal.getRoles().size());
            assertTrue(principal.getRoles().contains("admin"));
        }

        @Test
        @DisplayName("只有 userId 的构造应使用 userId 作为 username")
        void singleArgConstructorShouldUseUserIdAsUsername() {
            WsPrincipal principal = new WsPrincipal("u001");

            assertEquals("u001", principal.getUserId());
            assertEquals("u001", principal.getUsername());
        }

        @Test
        @DisplayName("单参数构造的 roles 应为空列表")
        void singleArgConstructorShouldHaveEmptyRoles() {
            WsPrincipal principal = new WsPrincipal("u001");
            assertNotNull(principal.getRoles());
            assertTrue(principal.getRoles().isEmpty());
        }

        @Test
        @DisplayName("userId 为 null 应抛出异常")
        void nullUserIdShouldThrowException() {
            assertThrows(NullPointerException.class, () -> new WsPrincipal(null, "name", Collections.emptyList()));
        }

        @Test
        @DisplayName("username 为 null 时应使用 userId 作为 username")
        void nullUsernameShouldFallbackToUserId() {
            WsPrincipal principal = new WsPrincipal("u001", null, Collections.emptyList());
            assertEquals("u001", principal.getUsername());
        }

        @Test
        @DisplayName("roles 为 null 时应初始化为空列表")
        void nullRolesShouldBeEmptyList() {
            WsPrincipal principal = new WsPrincipal("u001", "Alice", null);
            assertNotNull(principal.getRoles());
            assertTrue(principal.getRoles().isEmpty());
        }
    }

    @Nested
    @DisplayName("Principal 接口实现测试")
    class PrincipalInterfaceTests {

        @Test
        @DisplayName("getName() 应返回 userId")
        void getNameShouldReturnUserId() {
            WsPrincipal principal = new WsPrincipal("user123", "Bob", Collections.emptyList());
            assertEquals("user123", principal.getName());
        }

        @Test
        @DisplayName("getName() 应与 getUserId() 一致")
        void getNameShouldEqualGetUserId() {
            WsPrincipal principal = new WsPrincipal("abc");
            assertEquals(principal.getUserId(), principal.getName());
        }
    }

    @Nested
    @DisplayName("hasRole 测试")
    class HasRoleTests {

        private final WsPrincipal principal = new WsPrincipal("u1", "Test",
                Arrays.asList("admin", "user", "moderator"));

        @Test
        @DisplayName("拥有角色时 hasRole 应返回 true")
        void hasRoleShouldReturnTrueWhenRoleExists() {
            assertTrue(principal.hasRole("admin"));
            assertTrue(principal.hasRole("user"));
            assertTrue(principal.hasRole("moderator"));
        }

        @Test
        @DisplayName("不拥有角色时 hasRole 应返回 false")
        void hasRoleShouldReturnFalseWhenRoleAbsent() {
            assertFalse(principal.hasRole("superadmin"));
            assertFalse(principal.hasRole("guest"));
        }

        @Test
        @DisplayName("hasRole(null) 对空角色列表应返回 false")
        void hasRoleWithNullShouldReturnFalse() {
            WsPrincipal p = new WsPrincipal("u1");
            assertFalse(p.hasRole(null));
        }
    }

    @Nested
    @DisplayName("hasAllRoles 测试")
    class HasAllRolesTests {

        private final WsPrincipal principal = new WsPrincipal("u1", "Test",
                Arrays.asList("admin", "user", "moderator"));

        @Test
        @DisplayName("拥有所有指定角色时应返回 true")
        void shouldReturnTrueWhenHasAllRoles() {
            assertTrue(principal.hasAllRoles("admin", "user"));
            assertTrue(principal.hasAllRoles("admin"));
            assertTrue(principal.hasAllRoles("moderator", "user", "admin"));
        }

        @Test
        @DisplayName("缺少任一指定角色时应返回 false")
        void shouldReturnFalseWhenMissingAnyRole() {
            assertFalse(principal.hasAllRoles("admin", "superadmin"));
            assertFalse(principal.hasAllRoles("guest"));
        }

        @Test
        @DisplayName("空参数或无参数应返回 true")
        void shouldReturnTrueForEmptyOrNullArgs() {
            assertTrue(principal.hasAllRoles());
            assertTrue(principal.hasAllRoles((String[]) null));
        }
    }

    @Nested
    @DisplayName("roles 不可变测试")
    class RolesImmutabilityTests {

        @Test
        @DisplayName("getRoles() 返回的列表应不可修改")
        void getRolesShouldReturnUnmodifiableList() {
            WsPrincipal principal = new WsPrincipal("u1", "Test", Arrays.asList("admin"));
            List<String> roles = principal.getRoles();
            assertThrows(UnsupportedOperationException.class, () -> roles.add("newrole"));
        }

        @Test
        @DisplayName("构造后修改原始列表不应影响 principal 的 roles")
        void modifyingOriginalListShouldNotAffectPrincipal() {
            List<String> originalRoles = new java.util.ArrayList<>(Arrays.asList("admin"));
            WsPrincipal principal = new WsPrincipal("u1", "Test", originalRoles);
            originalRoles.add("hacker");
            assertFalse(principal.hasRole("hacker"));
            assertEquals(1, principal.getRoles().size());
        }
    }

    @Nested
    @DisplayName("扩展属性测试")
    class AttributeTests {

        @Test
        @DisplayName("setAttribute/getAttribute 应正确存取")
        void setAndGetAttributeShouldWork() {
            WsPrincipal principal = new WsPrincipal("u1");
            principal.setAttribute("tenantId", "t001");
            principal.setAttribute("deptId", 100L);

            assertEquals("t001", principal.getAttribute("tenantId"));
            assertEquals(100L, (long) principal.getAttribute("deptId"));
        }

        @Test
        @DisplayName("getAttribute 不存在的 key 应返回 null")
        void getAttributeAbsentKeyShouldReturnNull() {
            WsPrincipal principal = new WsPrincipal("u1");
            assertNull(principal.getAttribute("nonexistent"));
        }

        @Test
        @DisplayName("getAttributes 应返回可修改的 Map")
        void getAttributesShouldReturnModifiableMap() {
            WsPrincipal principal = new WsPrincipal("u1");
            principal.getAttributes().put("key", "value");
            assertEquals("value", principal.getAttribute("key"));
        }
    }

    @Nested
    @DisplayName("equals/hashCode 测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同 userId 的 Principal 应相等")
        void sameUserIdShouldBeEqual() {
            WsPrincipal p1 = new WsPrincipal("u001", "Alice", Arrays.asList("admin"));
            WsPrincipal p2 = new WsPrincipal("u001", "Bob", Collections.emptyList());

            assertEquals(p1, p2);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("不同 userId 的 Principal 应不相等")
        void differentUserIdShouldNotBeEqual() {
            WsPrincipal p1 = new WsPrincipal("u001");
            WsPrincipal p2 = new WsPrincipal("u002");

            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("同一对象应相等")
        void sameObjectShouldBeEqual() {
            WsPrincipal p = new WsPrincipal("u001");
            assertEquals(p, p);
        }

        @Test
        @DisplayName("与 null 比较应返回 false")
        void compareToNullShouldReturnFalse() {
            assertNotEquals(new WsPrincipal("u001"), null);
        }

        @Test
        @DisplayName("与不同类对象比较应返回 false")
        void compareToDifferentClassShouldReturnFalse() {
            assertNotEquals(new WsPrincipal("u001"), "u001");
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            WsPrincipal principal = new WsPrincipal("u001", "Alice", Arrays.asList("admin"));
            String str = principal.toString();
            assertTrue(str.contains("u001"));
            assertTrue(str.contains("Alice"));
            assertTrue(str.contains("admin"));
            assertTrue(str.contains("WsPrincipal"));
        }
    }
}
