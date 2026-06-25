package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StrUtilTest {

    @Test
    void isEmpty() {
        assertTrue(StrUtil.isEmpty(null));
        assertTrue(StrUtil.isEmpty(""));
        assertFalse(StrUtil.isEmpty("a"));
    }

    @Test
    void isBlank() {
        assertTrue(StrUtil.isBlank(null));
        assertTrue(StrUtil.isBlank("   "));
        assertFalse(StrUtil.isBlank(" a "));
    }

    @Test
    void defaultIfBlank() {
        assertEquals("default", StrUtil.defaultIfBlank(null, "default"));
        assertEquals("value", StrUtil.defaultIfBlank("value", "default"));
    }

    @Test
    void format() {
        assertEquals("Hello, World!", StrUtil.format("Hello, {}!", "World"));
        assertEquals("a,b,c", StrUtil.format("{},{},{}", "a", "b", "c"));
        assertEquals("a,{},c", StrUtil.format("{},{},{}", "a"));
    }

    @Test
    void toCamelCase() {
        assertEquals("userName", StrUtil.toCamelCase("user_name"));
        assertEquals("helloWorld", StrUtil.toCamelCase("HELLO_WORLD"));
    }

    @Test
    void toSnakeCase() {
        assertEquals("user_name", StrUtil.toSnakeCase("userName"));
        assertEquals("hello_world", StrUtil.toSnakeCase("HelloWorld"));
    }

    @Test
    void truncate() {
        assertEquals("hello...", StrUtil.truncate("hello world", 5));
        assertEquals("hello world", StrUtil.truncate("hello world", 20));
    }

    @Test
    void leftRight() {
        assertEquals("hel", StrUtil.left("hello", 3));
        assertEquals("llo", StrUtil.right("hello", 3));
    }

    @Test
    void removePrefixSuffix() {
        assertEquals("bar", StrUtil.removePrefix("foobar", "foo"));
        assertEquals("foo", StrUtil.removeSuffix("foobar", "bar"));
    }

    @Test
    void join() {
        assertEquals("a,b,c", StrUtil.join(Arrays.asList("a", "b", "c"), ","));
        assertEquals("1-2-3", StrUtil.join(new Object[]{1, 2, 3}, "-"));
    }

    @Test
    void capitalize() {
        assertEquals("Hello", StrUtil.capitalize("hello"));
        assertEquals("Hello", StrUtil.capitalize("Hello"));
    }
}
