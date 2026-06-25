package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base64UtilTest {

    @Test
    void encodeDecode() {
        String original = "Hello, World!";
        String encoded = Base64Util.encode(original);
        assertNotNull(encoded);
        String decoded = Base64Util.decode(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void encodeDecodeUrlSafe() {
        String original = "test-data+foo/bar";
        String encoded = Base64Util.encodeUrlSafe(original);
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("/"));
        String decoded = Base64Util.decodeUrlSafe(encoded);
        assertEquals(original, decoded);
    }
}
