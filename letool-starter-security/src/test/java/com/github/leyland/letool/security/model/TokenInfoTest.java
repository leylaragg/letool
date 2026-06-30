package com.github.leyland.letool.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenInfo 单元测试
 */
@DisplayName("TokenInfo 单元测试")
class TokenInfoTest {

    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数应创建空对象且 tokenType 默认为 Bearer")
        void noArgsConstructorShouldCreateWithDefaultTokenType() {
            TokenInfo tokenInfo = new TokenInfo();

            assertNull(tokenInfo.getAccessToken());
            assertNull(tokenInfo.getRefreshToken());
            assertEquals(0L, tokenInfo.getExpiresIn());
            assertEquals("Bearer", tokenInfo.getTokenType());
        }

        @Test
        @DisplayName("有参构造函数应正确设置 accessToken、refreshToken 和 expiresIn")
        void parameterizedConstructorShouldSetProperties() {
            TokenInfo tokenInfo = new TokenInfo("access-abc", "refresh-xyz", 1800);

            assertEquals("access-abc", tokenInfo.getAccessToken());
            assertEquals("refresh-xyz", tokenInfo.getRefreshToken());
            assertEquals(1800L, tokenInfo.getExpiresIn());
            assertEquals("Bearer", tokenInfo.getTokenType());
        }

        @Test
        @DisplayName("有参构造函数 expiresIn 为 0 时应正常创建")
        void parameterizedConstructorWithZeroExpiresIn() {
            TokenInfo tokenInfo = new TokenInfo("access", "refresh", 0);

            assertEquals(0L, tokenInfo.getExpiresIn());
        }

        @Test
        @DisplayName("有参构造函数 expiresIn 为负值时应正常创建")
        void parameterizedConstructorWithNegativeExpiresIn() {
            TokenInfo tokenInfo = new TokenInfo("access", "refresh", -1);

            assertEquals(-1L, tokenInfo.getExpiresIn());
        }
    }

    @Nested
    @DisplayName("属性存取测试")
    class PropertyAccessTests {

        @Test
        @DisplayName("应正确存取 accessToken")
        void shouldSetAndGetAccessToken() {
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setAccessToken("my-access-token");
            assertEquals("my-access-token", tokenInfo.getAccessToken());
        }

        @Test
        @DisplayName("应正确存取 refreshToken")
        void shouldSetAndGetRefreshToken() {
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setRefreshToken("my-refresh-token");
            assertEquals("my-refresh-token", tokenInfo.getRefreshToken());
        }

        @Test
        @DisplayName("应正确存取 expiresIn")
        void shouldSetAndGetExpiresIn() {
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setExpiresIn(3600L);
            assertEquals(3600L, tokenInfo.getExpiresIn());
        }

        @Test
        @DisplayName("应正确存取 tokenType")
        void shouldSetAndGetTokenType() {
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setTokenType("Mac");
            assertEquals("Mac", tokenInfo.getTokenType());
        }

        @Test
        @DisplayName("tokenType 默认为 Bearer")
        void tokenTypeShouldDefaultToBearer() {
            TokenInfo tokenInfo = new TokenInfo("at", "rt", 1800);
            assertEquals("Bearer", tokenInfo.getTokenType());
        }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTests {

        @Test
        @DisplayName("TokenInfo 应支持 Java 序列化")
        void shouldBeSerializable() throws Exception {
            TokenInfo tokenInfo = new TokenInfo("access-token", "refresh-token", 7200);
            tokenInfo.setTokenType("Bearer");

            // 序列化
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(tokenInfo);
            oos.close();

            // 反序列化
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            TokenInfo deserialized = (TokenInfo) ois.readObject();

            assertEquals("access-token", deserialized.getAccessToken());
            assertEquals("refresh-token", deserialized.getRefreshToken());
            assertEquals(7200L, deserialized.getExpiresIn());
            assertEquals("Bearer", deserialized.getTokenType());
        }
    }
}
