package com.github.leyland.letool.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginRequest 单元测试
 */
@DisplayName("LoginRequest 单元测试")
class LoginRequestTest {

    @Nested
    @DisplayName("属性存取测试")
    class PropertyAccessTests {

        @Test
        @DisplayName("应正确存取 username")
        void shouldSetAndGetUsername() {
            LoginRequest request = new LoginRequest();
            request.setUsername("admin");
            assertEquals("admin", request.getUsername());
        }

        @Test
        @DisplayName("应正确存取 password")
        void shouldSetAndGetPassword() {
            LoginRequest request = new LoginRequest();
            request.setPassword("secret123");
            assertEquals("secret123", request.getPassword());
        }

        @Test
        @DisplayName("应正确存取 captcha")
        void shouldSetAndGetCaptcha() {
            LoginRequest request = new LoginRequest();
            request.setCaptcha("ABCD");
            assertEquals("ABCD", request.getCaptcha());
        }

        @Test
        @DisplayName("captcha 默认为 null")
        void captchaShouldDefaultToNull() {
            LoginRequest request = new LoginRequest();
            assertNull(request.getCaptcha());
        }

        @Test
        @DisplayName("username 默认为 null")
        void usernameShouldDefaultToNull() {
            LoginRequest request = new LoginRequest();
            assertNull(request.getUsername());
        }

        @Test
        @DisplayName("password 默认为 null")
        void passwordShouldDefaultToNull() {
            LoginRequest request = new LoginRequest();
            assertNull(request.getPassword());
        }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTests {

        @Test
        @DisplayName("应支持 Java 序列化")
        void shouldBeSerializable() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("admin");
            request.setPassword("pass123");
            request.setCaptcha("XYZ");

            // 序列化
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(request);
            oos.close();

            // 反序列化
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            LoginRequest deserialized = (LoginRequest) ois.readObject();

            assertEquals("admin", deserialized.getUsername());
            assertEquals("pass123", deserialized.getPassword());
            assertEquals("XYZ", deserialized.getCaptcha());
        }
    }
}
