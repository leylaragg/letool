package io.github.leylaragg.letool.cipher.hash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MD5 遗留校验和向量测试。
 */
class Md5UtilTest {

    /**
     * 验证 MD5 的 {@code abc} 公开向量。
     */
    @SuppressWarnings("deprecation")
    @Test
    void shouldMatchMd5AbcVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5Util.md5("abc"));
    }
}
