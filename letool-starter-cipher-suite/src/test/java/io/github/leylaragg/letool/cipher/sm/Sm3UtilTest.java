package io.github.leylaragg.letool.cipher.sm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SM3 标准向量测试。
 */
class Sm3UtilTest {

    /**
     * 验证字符串 {@code abc} 的国家标准公开向量。
     */
    @Test
    void shouldMatchOfficialAbcVector() {
        assertEquals(
                "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                Sm3Util.sm3("abc"));
    }
}
