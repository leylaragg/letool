package com.github.leyland.letool.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginResponse 单元测试
 */
@DisplayName("LoginResponse 单元测试")
class LoginResponseTest {

    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数应创建空对象")
        void noArgsConstructorShouldCreateEmptyObject() {
            LoginResponse response = new LoginResponse();

            assertNull(response.getTokenInfo());
            assertNull(response.getUserId());
            assertNull(response.getUsername());
            assertNull(response.getNickname());
            assertEquals(0L, response.getLoginTime());
        }

        @Test
        @DisplayName("有参构造函数应正确设置属性")
        void parameterizedConstructorShouldSetProperties() {
            TokenInfo tokenInfo = new TokenInfo("access-token", "refresh-token", 3600);
            Long userId = 1L;
            String username = "admin";

            LoginResponse response = new LoginResponse(tokenInfo, userId, username);

            assertEquals(tokenInfo, response.getTokenInfo());
            assertEquals(1L, response.getUserId());
            assertEquals("admin", response.getUsername());
            assertNotNull(response.getLoginTime());
            assertTrue(response.getLoginTime() > 0);
        }

        @Test
        @DisplayName("有参构造函数的 loginTime 应接近当前时间")
        void loginTimeShouldBeCloseToCurrentTime() {
            long before = System.currentTimeMillis();
            LoginResponse response = new LoginResponse(
                    new TokenInfo("at", "rt", 3600), 1L, "admin");
            long after = System.currentTimeMillis();

            assertTrue(response.getLoginTime() >= before);
            assertTrue(response.getLoginTime() <= after);
        }

        @Test
        @DisplayName("有参构造函数的 nickname 默认为 null")
        void nicknameShouldDefaultToNull() {
            LoginResponse response = new LoginResponse(
                    new TokenInfo("at", "rt", 3600), 1L, "admin");

            assertNull(response.getNickname());
        }
    }

    @Nested
    @DisplayName("属性存取测试")
    class PropertyAccessTests {

        @Test
        @DisplayName("应正确存取 tokenInfo")
        void shouldSetAndGetTokenInfo() {
            LoginResponse response = new LoginResponse();
            TokenInfo tokenInfo = new TokenInfo("acc", "ref", 7200);
            response.setTokenInfo(tokenInfo);
            assertEquals(tokenInfo, response.getTokenInfo());
        }

        @Test
        @DisplayName("应正确存取 userId")
        void shouldSetAndGetUserId() {
            LoginResponse response = new LoginResponse();
            response.setUserId(99L);
            assertEquals(99L, response.getUserId());
        }

        @Test
        @DisplayName("应正确存取 username")
        void shouldSetAndGetUsername() {
            LoginResponse response = new LoginResponse();
            response.setUsername("testuser");
            assertEquals("testuser", response.getUsername());
        }

        @Test
        @DisplayName("应正确存取 nickname")
        void shouldSetAndGetNickname() {
            LoginResponse response = new LoginResponse();
            response.setNickname("Test User");
            assertEquals("Test User", response.getNickname());
        }

        @Test
        @DisplayName("应正确存取 loginTime")
        void shouldSetAndGetLoginTime() {
            LoginResponse response = new LoginResponse();
            response.setLoginTime(123456789L);
            assertEquals(123456789L, response.getLoginTime());
        }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTests {

        @Test
        @DisplayName("应支持 Java 序列化")
        void shouldBeSerializable() throws Exception {
            TokenInfo tokenInfo = new TokenInfo("access-token", "refresh-token", 1800);
            LoginResponse response = new LoginResponse(tokenInfo, 10L, "admin");
            response.setNickname("Administrator");
            response.setLoginTime(1000000L);

            // 序列化
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(response);
            oos.close();

            // 反序列化
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            LoginResponse deserialized = (LoginResponse) ois.readObject();

            assertEquals("access-token", deserialized.getTokenInfo().getAccessToken());
            assertEquals("refresh-token", deserialized.getTokenInfo().getRefreshToken());
            assertEquals(10L, deserialized.getUserId());
            assertEquals("admin", deserialized.getUsername());
            assertEquals("Administrator", deserialized.getNickname());
            assertEquals(1000000L, deserialized.getLoginTime());
        }
    }
}
