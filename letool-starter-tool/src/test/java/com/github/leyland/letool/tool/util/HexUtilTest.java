package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.encoding.EncodingErrorCode;
import com.github.leyland.letool.tool.encoding.EncodingOperationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 十六进制编解码关键契约测试。
 */
class HexUtilTest {

    /**
     * 验证大小写十六进制文本都可以无损解码。
     */
    @Test
    void shouldRoundTripLowerAndUpperCaseHex() {
        byte[] original = {(byte) 0x80, 0x0a, (byte) 0xff};

        assertEquals("800aff", HexUtil.encodeHex(original));
        assertEquals("800AFF", HexUtil.encodeHex(original, true));
        assertEquals("你好", HexUtil.decodeHexToStr(HexUtil.encodeHex("你好")));
    }

    /**
     * 验证现有空值透传契约保持兼容。
     */
    @Test
    void shouldPreserveNullPassThroughContract() {
        assertNull(HexUtil.encodeHex((byte[]) null));
        assertNull(HexUtil.decodeHex(null));
    }

    /**
     * 验证奇数长度和非法字符都转换为稳定编码异常。
     */
    @Test
    void shouldRejectMalformedHexWithStableError() {
        EncodingOperationException oddLength = assertThrows(
                EncodingOperationException.class,
                () -> HexUtil.decodeHex("abc")
        );
        EncodingOperationException invalidCharacter = assertThrows(
                EncodingOperationException.class,
                () -> HexUtil.decodeHex("00xz")
        );

        assertEquals(EncodingErrorCode.HEX_DECODE_FAILED.getCode(), oddLength.getCode());
        assertEquals(EncodingErrorCode.HEX_DECODE_FAILED.getCode(), invalidCharacter.getCode());
        assertFalse(invalidCharacter.getMessage().contains("00xz"));
    }

    /**
     * 验证大小写混合的十六进制文本能够严格解码。
     */
    @Test
    void shouldDecodeMixedCaseHexText() {
        assertArrayEquals(
                new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0xef},
                HexUtil.decodeHex("aBcDeF"));
    }
}
