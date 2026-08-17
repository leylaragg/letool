package io.github.leylaragg.letool.cipher.hash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHA-2 公开向量测试。
 */
class ShaUtilTest {

    /**
     * 验证 SHA-256 的 {@code abc} 公开向量。
     */
    @Test
    void shouldMatchSha256AbcVector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ShaUtil.sha256("abc"));
    }

    /**
     * 验证 SHA-512 的 {@code abc} 公开向量。
     */
    @Test
    void shouldMatchSha512AbcVector() {
        assertEquals(
                "ddaf35a193617abacc417349ae204131"
                        + "12e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd"
                        + "454d4423643ce80e2a9ac94fa54ca49f",
                ShaUtil.sha512("abc"));
    }
}
