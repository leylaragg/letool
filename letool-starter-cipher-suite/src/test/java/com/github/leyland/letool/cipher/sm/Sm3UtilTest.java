package com.github.leyland.letool.cipher.sm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 国密 SM3 哈希算法单元测试.
 */
@DisplayName("国密 SM3 哈希测试")
class Sm3UtilTest {

    // ===================== 字符串 SM3 测试 =====================

    @Nested
    @DisplayName("字符串 SM3 测试")
    class StringSm3Tests {

        @Test
        @DisplayName("SM3 哈希值长度应为 64 位十六进制字符")
        void shouldReturn64HexChars() {
            String hash = Sm3Util.sm3("hello");
            assertNotNull(hash);
            assertEquals(64, hash.length(), "SM3 应返回 64 位十六进制字符串");
        }

        @Test
        @DisplayName("同一输入多次计算 SM3 应得到相同哈希值")
        void shouldBeDeterministic() {
            String input = "SM3 deterministic test";
            String hash1 = Sm3Util.sm3(input);
            String hash2 = Sm3Util.sm3(input);
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("不同输入应产生不同 SM3 哈希值")
        void shouldProduceDifferentHashForDifferentInput() {
            String hash1 = Sm3Util.sm3("hello");
            String hash2 = Sm3Util.sm3("world");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("仅大小写不同的输入应产生不同 SM3 哈希值")
        void shouldBeCaseSensitive() {
            assertNotEquals(Sm3Util.sm3("Hello"), Sm3Util.sm3("hello"));
        }

        @Test
        @DisplayName("中文文本 SM3 计算应正常")
        void shouldHashChinese() {
            String hash = Sm3Util.sm3("你好，世界！国密 SM3 哈希测试");
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("长文本 SM3 计算应正常")
        void shouldHashLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("国密SM3长文本测试-").append(i);
            }
            String hash = Sm3Util.sm3(sb.toString());
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("SM3 值应仅包含小写十六进制字符")
        void shouldContainOnlyLowercaseHex() {
            String hash = Sm3Util.sm3("test");
            assertTrue(hash.matches("^[0-9a-f]{64}$"), "SM3 应为小写十六进制字符");
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNullString() {
            assertNull(Sm3Util.sm3((String) null));
        }

        @Test
        @DisplayName("SM3 与 SHA-256 同一输入应产生不同哈希值")
        void sm3AndSha256ShouldDiffer() {
            String input = "compare national and standard";
            String sm3Hash = Sm3Util.sm3(input);
            String sha256Hash = com.github.leyland.letool.cipher.hash.ShaUtil.sha256(input);
            assertNotEquals(sm3Hash, sha256Hash,
                    "SM3 和 SHA-256 是不同的算法，应产生不同哈希值");
        }
    }

    // ===================== 字节数组 SM3 测试 =====================

    @Nested
    @DisplayName("字节数组 SM3 测试")
    class ByteArraySm3Tests {

        @Test
        @DisplayName("字节数组 SM3 与同内容字符串 SM3 应一致")
        void shouldMatchStringHash() {
            String input = "byte array sm3 test";
            String stringHash = Sm3Util.sm3(input);
            String bytesHash = Sm3Util.sm3(input.getBytes(StandardCharsets.UTF_8));
            assertEquals(stringHash, bytesHash);
        }

        @Test
        @DisplayName("不同字节数组应产生不同 SM3")
        void shouldProduceDifferentHashForDifferentBytes() {
            byte[] data1 = {0x01, 0x02, 0x03};
            byte[] data2 = {0x01, 0x02, 0x04};
            assertNotEquals(Sm3Util.sm3(data1), Sm3Util.sm3(data2));
        }

        @Test
        @DisplayName("null 字节数组应返回 null")
        void shouldReturnNullForNullBytes() {
            assertNull(Sm3Util.sm3((byte[]) null));
        }

        @Test
        @DisplayName("空字节数组 SM3 应返回 64 位哈希")
        void shouldHashEmptyBytes() {
            String hash = Sm3Util.sm3(new byte[0]);
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }
    }
}
