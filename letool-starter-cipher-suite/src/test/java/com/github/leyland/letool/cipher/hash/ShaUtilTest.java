package com.github.leyland.letool.cipher.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SHA 哈希工具单元测试 —— 覆盖 SHA-256 和 SHA-512.
 */
@DisplayName("SHA 哈希测试")
class ShaUtilTest {

    // ===================== SHA-256 测试 =====================

    @Nested
    @DisplayName("SHA-256 测试")
    class Sha256Tests {

        @Test
        @DisplayName("SHA-256 哈希值长度应为 64 位十六进制字符")
        void shouldReturn64HexChars() {
            String hash = ShaUtil.sha256("hello");
            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA-256 应返回 64 位十六进制字符串");
        }

        @Test
        @DisplayName("同一输入多次计算 SHA-256 应得到相同哈希值")
        void shouldBeDeterministic() {
            String input = "SHA-256 deterministic test";
            assertEquals(ShaUtil.sha256(input), ShaUtil.sha256(input));
        }

        @Test
        @DisplayName("不同输入应产生不同 SHA-256 哈希值")
        void shouldProduceDifferentHashForDifferentInput() {
            assertNotEquals(ShaUtil.sha256("hello"), ShaUtil.sha256("world"));
        }

        @Test
        @DisplayName("SHA-256 中文文本哈希计算应正常")
        void shouldHashChinese() {
            String hash = ShaUtil.sha256("你好，世界！SHA-256 测试");
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("SHA-256 值应仅包含小写十六进制字符")
        void shouldContainOnlyLowercaseHex() {
            String hash = ShaUtil.sha256("test");
            assertTrue(hash.matches("^[0-9a-f]{64}$"), "SHA-256 应为小写十六进制字符");
        }

        @Test
        @DisplayName("SHA-256 null 输入应返回 null")
        void shouldReturnNullForNullString() {
            assertNull(ShaUtil.sha256((String) null));
        }

        @Test
        @DisplayName("字节数组 SHA-256 与同内容字符串 SHA-256 应一致")
        void shouldMatchStringHash() {
            String input = "byte array sha256";
            assertEquals(ShaUtil.sha256(input), ShaUtil.sha256(input.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("SHA-256 null 字节数组应返回 null")
        void shouldReturnNullForNullBytes() {
            assertNull(ShaUtil.sha256((byte[]) null));
        }
    }

    // ===================== SHA-512 测试 =====================

    @Nested
    @DisplayName("SHA-512 测试")
    class Sha512Tests {

        @Test
        @DisplayName("SHA-512 哈希值长度应为 128 位十六进制字符")
        void shouldReturn128HexChars() {
            String hash = ShaUtil.sha512("hello");
            assertNotNull(hash);
            assertEquals(128, hash.length(), "SHA-512 应返回 128 位十六进制字符串");
        }

        @Test
        @DisplayName("同一输入多次计算 SHA-512 应得到相同哈希值")
        void shouldBeDeterministic() {
            String input = "SHA-512 deterministic test";
            assertEquals(ShaUtil.sha512(input), ShaUtil.sha512(input));
        }

        @Test
        @DisplayName("不同输入应产生不同 SHA-512 哈希值")
        void shouldProduceDifferentHashForDifferentInput() {
            assertNotEquals(ShaUtil.sha512("hello"), ShaUtil.sha512("world"));
        }

        @Test
        @DisplayName("同一输入 SHA-256 和 SHA-512 应产生不同哈希值")
        void sha256AndSha512ShouldDiffer() {
            String input = "compare algorithms";
            assertNotEquals(ShaUtil.sha256(input), ShaUtil.sha512(input));
        }

        @Test
        @DisplayName("SHA-512 null 输入应返回 null")
        void shouldReturnNullForNullString() {
            assertNull(ShaUtil.sha512((String) null));
        }
    }

    // ===================== 通用 hash() 方法测试 =====================

    @Nested
    @DisplayName("通用 hash() 方法测试")
    class GenericHashTests {

        @Test
        @DisplayName("hash(字符串, SHA-256) 应与 sha256() 结果一致")
        void hashStringSha256ShouldMatch() {
            String input = "generic hash test";
            assertEquals(ShaUtil.sha256(input), ShaUtil.hash(input, "SHA-256"));
        }

        @Test
        @DisplayName("hash(字符串, SHA-512) 应与 sha512() 结果一致")
        void hashStringSha512ShouldMatch() {
            String input = "generic hash test";
            assertEquals(ShaUtil.sha512(input), ShaUtil.hash(input, "SHA-512"));
        }

        @Test
        @DisplayName("hash(字符串, SHA-1) 应返回非空结果")
        void hashStringSha1ShouldWork() {
            String hash = ShaUtil.hash("test", "SHA-1");
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        @Test
        @DisplayName("hash(字节数组, SHA-256) 应与 sha256(字节数组) 一致")
        void hashBytesSha256ShouldMatch() {
            byte[] data = "bytes test".getBytes(StandardCharsets.UTF_8);
            assertEquals(ShaUtil.sha256(data), ShaUtil.hash(data, "SHA-256"));
        }

        @Test
        @DisplayName("hash(字符串, null) 应返回 null")
        void shouldReturnNullForNullString() {
            assertNull(ShaUtil.hash((String) null, "SHA-256"));
        }

        @Test
        @DisplayName("hash(字节数组, null) 应返回 null")
        void shouldReturnNullForNullBytes() {
            assertNull(ShaUtil.hash((byte[]) null, "SHA-256"));
        }

        @Test
        @DisplayName("hash 不支持的算法应抛出异常")
        void shouldThrowForUnsupportedAlgorithm() {
            assertThrows(Exception.class, () -> ShaUtil.hash("test", "INVALID-ALGO"));
        }
    }
}
