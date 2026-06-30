package com.github.leyland.letool.security.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthMode 枚举单元测试
 */
@DisplayName("AuthMode 枚举单元测试")
class AuthModeTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("应包含 JWT 枚举值")
        void shouldHaveJwtValue() {
            AuthMode jwt = AuthMode.valueOf("JWT");
            assertEquals(AuthMode.JWT, jwt);
        }

        @Test
        @DisplayName("应包含 JWT_REDIS 枚举值")
        void shouldHaveJwtRedisValue() {
            AuthMode jwtRedis = AuthMode.valueOf("JWT_REDIS");
            assertEquals(AuthMode.JWT_REDIS, jwtRedis);
        }

        @Test
        @DisplayName("应包含 SESSION 枚举值")
        void shouldHaveSessionValue() {
            AuthMode session = AuthMode.valueOf("SESSION");
            assertEquals(AuthMode.SESSION, session);
        }

        @Test
        @DisplayName("应恰好包含 3 个枚举常量")
        void shouldHaveExactlyThreeConstants() {
            AuthMode[] modes = AuthMode.values();
            assertEquals(3, modes.length);
        }
    }

    @Nested
    @DisplayName("name() 和 ordinal() 测试")
    class NameAndOrdinalTests {

        @Test
        @DisplayName("JWT 的 name 应为 JWT")
        void jwtNameShouldBeJwt() {
            assertEquals("JWT", AuthMode.JWT.name());
        }

        @Test
        @DisplayName("JWT_REDIS 的 name 应为 JWT_REDIS")
        void jwtRedisNameShouldBeJwtRedis() {
            assertEquals("JWT_REDIS", AuthMode.JWT_REDIS.name());
        }

        @Test
        @DisplayName("SESSION 的 name 应为 SESSION")
        void sessionNameShouldBeSession() {
            assertEquals("SESSION", AuthMode.SESSION.name());
        }

        @Test
        @DisplayName("枚举常量的 ordinal 应按声明顺序排列")
        void ordinalsShouldFollowDeclarationOrder() {
            assertEquals(0, AuthMode.JWT.ordinal());
            assertEquals(1, AuthMode.JWT_REDIS.ordinal());
            assertEquals(2, AuthMode.SESSION.ordinal());
        }
    }

    @Nested
    @DisplayName("valueOf 测试")
    class ValueOfTests {

        @Test
        @DisplayName("传入合法名称应成功解析")
        void shouldParseValidNames() {
            assertEquals(AuthMode.JWT, AuthMode.valueOf("JWT"));
            assertEquals(AuthMode.JWT_REDIS, AuthMode.valueOf("JWT_REDIS"));
            assertEquals(AuthMode.SESSION, AuthMode.valueOf("SESSION"));
        }

        @Test
        @DisplayName("传入非法名称应抛出 IllegalArgumentException")
        void shouldThrowForInvalidName() {
            assertThrows(IllegalArgumentException.class, () -> AuthMode.valueOf("OAUTH"));
        }

        @Test
        @DisplayName("传入 null 应抛出 NullPointerException")
        void shouldThrowForNull() {
            assertThrows(NullPointerException.class, () -> AuthMode.valueOf(null));
        }

        @Test
        @DisplayName("传入空字符串应抛出 IllegalArgumentException")
        void shouldThrowForEmptyString() {
            assertThrows(IllegalArgumentException.class, () -> AuthMode.valueOf(""));
        }
    }
}
