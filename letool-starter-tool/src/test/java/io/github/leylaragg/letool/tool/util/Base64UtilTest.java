package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.encoding.EncodingErrorCode;
import io.github.leylaragg.letool.tool.encoding.EncodingOperationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base64 编解码关键契约测试。
 */
class Base64UtilTest {

    /**
     * 验证标准 Base64 字符串和字节入口可以无损往返。
     */
    @Test
    void shouldRoundTripStandardTextAndBytes() {
        String original = "你好，Letool";
        String encoded = Base64Util.encode(original);

        assertEquals(original, Base64Util.decode(encoded));
        assertArrayEquals(
                original.getBytes(StandardCharsets.UTF_8),
                Base64Util.decodeToBytes(encoded)
        );
    }

    /**
     * 验证 URL 安全编码明确区分有填充和无填充两种入口。
     */
    @Test
    void shouldExposePaddedAndUnpaddedUrlSafeEncoding() {
        byte[] original = {(byte) 0xfb};

        String padded = Base64Util.encodeUrlSafe(original);
        String unpadded = Base64Util.encodeUrlSafeWithoutPadding(original);

        assertTrue(padded.endsWith("=="));
        assertFalse(unpadded.contains("="));
        assertArrayEquals(original, Base64Util.decodeUrlSafeToBytes(padded));
        assertArrayEquals(original, Base64Util.decodeUrlSafeToBytes(unpadded));
    }

    /**
     * 验证非法 Base64 文本转换为不泄漏原文的稳定编码异常。
     */
    @Test
    void shouldRejectMalformedBase64WithStableError() {
        EncodingOperationException exception = assertThrows(
                EncodingOperationException.class,
                () -> Base64Util.decode("敏感原文不是Base64")
        );

        assertEquals(EncodingErrorCode.BASE64_DECODE_FAILED.getCode(), exception.getCode());
        assertFalse(exception.getMessage().contains("敏感原文"));
    }
}
