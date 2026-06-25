package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpelUtilTest {

    @Test
    void evalSimple() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 10);
        vars.put("y", 20);
        Integer result = SpelUtil.eval("#x + #y", null, vars);
        assertEquals(30, result);
    }

    @Test
    void evalWithRoot() {
        Map<String, Object> root = new HashMap<>();
        root.put("name", "test");
        String result = SpelUtil.eval("[name]", root);
        assertEquals("test", result);
    }

    @Test
    void evalTemplate() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "World");
        String result = SpelUtil.evalTemplate("Hello, #{name}!", vars);
        assertEquals("Hello, World!", result);
    }
}
