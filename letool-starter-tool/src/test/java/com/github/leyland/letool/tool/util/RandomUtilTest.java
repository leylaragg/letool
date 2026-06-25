package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomUtilTest {

    @Test
    void nextIntRange() {
        for (int i = 0; i < 100; i++) {
            int val = RandomUtil.nextInt(10, 20);
            assertTrue(val >= 10 && val <= 20);
        }
    }

    @Test
    void randomNumbers() {
        String s = RandomUtil.randomNumbers(6);
        assertEquals(6, s.length());
        assertTrue(s.matches("\\d+"));
    }

    @Test
    void randomLetters() {
        String s = RandomUtil.randomLetters(8);
        assertEquals(8, s.length());
        assertTrue(s.matches("[a-zA-Z]+"));
    }

    @Test
    void randomString() {
        String s = RandomUtil.randomString(10);
        assertEquals(10, s.length());
    }

    @Test
    void randomCode() {
        String code = RandomUtil.randomCode(4);
        assertEquals(4, code.length());
        assertTrue(code.matches("\\d{4}"));
    }
}
