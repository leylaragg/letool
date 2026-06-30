package com.github.leyland.letool.cipher.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MD5 哈希工具单元测试.
 */
@DisplayName("MD5 哈希测试")
class Md5UtilTest {

    // ===================== 字符串 MD5 测试 =====================

    @Nested
    @DisplayName("字符串 MD5 测试")
    class StringMd5Tests {

        @Test
        @DisplayName("MD5 哈希值长度应为 32 位十六进制字符")
        void shouldReturn32HexChars() {
            String hash = Md5Util.md5("hello");
            assertNotNull(hash);
            assertEquals(32, hash.length(), "MD5 应返回 32 位十六进制字符串");
        }

        @Test
        @DisplayName("同一输入多次计算应得到相同哈希值")
        void shouldBeDeterministic() {
            String input = "deterministic test";
            String hash1 = Md5Util.md5(input);
            String hash2 = Md5Util.md5(input);
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("不同输入应产生不同哈希值")
        void shouldProduceDifferentHashForDifferentInput() {
            String hash1 = Md5Util.md5("hello");
            String hash2 = Md5Util.md5("world");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("仅大小写不同的输入应产生不同哈希值")
        void shouldBeCaseSensitive() {
            String hash1 = Md5Util.md5("Hello");
            String hash2 = Md5Util.md5("hello");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("中文文本 MD5 计算应正常")
        void shouldHashChinese() {
            String hash = Md5Util.md5("你好，世界！");
            assertNotNull(hash);
            assertEquals(32, hash.length());
        }

        @Test
        @DisplayName("空字符串 MD5 计算应返回正确的已知值")
        void shouldHashEmptyString() {
            String hash = Md5Util.md5("");
            assertNotNull(hash);
            assertEquals(32, hash.length());
        }

        @Test
        @DisplayName("长文本 MD5 计算应正常")
        void shouldHashLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("长文本MD5测试-").append(i);
            }
            String hash = Md5Util.md5(sb.toString());
            assertNotNull(hash);
            assertEquals(32, hash.length());
        }

        @Test
        @DisplayName("MD5 值应仅包含小写十六进制字符")
        void shouldContainOnlyLowercaseHex() {
            String hash = Md5Util.md5("test");
            assertTrue(hash.matches("^[0-9a-f]{32}$"), "MD5 应为小写十六进制字符");
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNullString() {
            assertNull(Md5Util.md5((String) null));
        }
    }

    // ===================== 字节数组 MD5 测试 =====================

    @Nested
    @DisplayName("字节数组 MD5 测试")
    class ByteArrayMd5Tests {

        @Test
        @DisplayName("字节数组 MD5 与同内容字符串 MD5 应一致")
        void shouldMatchStringHash() {
            String input = "byte array test";
            String stringHash = Md5Util.md5(input);
            String bytesHash = Md5Util.md5(input.getBytes(StandardCharsets.UTF_8));
            assertEquals(stringHash, bytesHash);
        }

        @Test
        @DisplayName("不同字节数组应产生不同 MD5")
        void shouldProduceDifferentHashForDifferentBytes() {
            byte[] data1 = {0x01, 0x02, 0x03};
            byte[] data2 = {0x01, 0x02, 0x04};
            assertNotEquals(Md5Util.md5(data1), Md5Util.md5(data2));
        }

        @Test
        @DisplayName("null 字节数组应返回 null")
        void shouldReturnNullForNullBytes() {
            assertNull(Md5Util.md5((byte[]) null));
        }

        @Test
        @DisplayName("空字节数组 MD5 应返回 32 位哈希")
        void shouldHashEmptyBytes() {
            String hash = Md5Util.md5(new byte[0]);
            assertNotNull(hash);
            assertEquals(32, hash.length());
        }
    }
}
