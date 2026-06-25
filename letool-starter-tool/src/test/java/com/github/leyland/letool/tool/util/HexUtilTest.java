package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HexUtilTest {

    @Test
    void encodeDecode() {
        String original = "Hello";
        String hex = HexUtil.encodeHex(original);
        assertNotNull(hex);
        String decoded = HexUtil.decodeHexToStr(hex);
        assertEquals(original, decoded);
    }

    @Test
    void encodeUpperCase() {
        String hex = HexUtil.encodeHex("A".getBytes(), true);
        assertTrue(hex.equals(hex.toUpperCase()));
    }

    @Test
    void nullSafety() {
        assertNull(HexUtil.encodeHex((byte[]) null));
        assertNull(HexUtil.decodeHex(null));
    }
}
