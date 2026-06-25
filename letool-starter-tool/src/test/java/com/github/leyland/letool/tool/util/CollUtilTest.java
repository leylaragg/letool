package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CollUtilTest {

    @Test
    void isEmpty() {
        assertTrue(CollUtil.isEmpty((List<?>) null));
        assertTrue(CollUtil.isEmpty(Collections.emptyList()));
        assertFalse(CollUtil.isEmpty(Arrays.asList(1, 2)));
        assertTrue(CollUtil.isEmpty((Map<?, ?>) null));
        assertFalse(CollUtil.isEmpty(Collections.singletonMap("k", "v")));
    }

    @Test
    void newArrayList() {
        List<String> list = CollUtil.newArrayList("a", "b", "c");
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
    }

    @Test
    void intersection() {
        List<Integer> a = Arrays.asList(1, 2, 3, 4);
        List<Integer> b = Arrays.asList(3, 4, 5, 6);
        List<Integer> result = CollUtil.intersection(a, b);
        assertEquals(new HashSet<>(Arrays.asList(3, 4)), new HashSet<>(result));
    }

    @Test
    void union() {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(3, 4, 5);
        List<Integer> result = CollUtil.union(a, b);
        assertEquals(5, result.size());
    }

    @Test
    void subtract() {
        List<Integer> a = Arrays.asList(1, 2, 3, 4);
        List<Integer> b = Arrays.asList(3, 4);
        List<Integer> result = CollUtil.subtract(a, b);
        assertEquals(Arrays.asList(1, 2), result);
    }

    @Test
    void partition() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
        List<List<Integer>> parts = CollUtil.partition(list, 2);
        assertEquals(3, parts.size());
        assertEquals(2, parts.get(0).size());
        assertEquals(1, parts.get(2).size());
    }

    @Test
    void extract() {
        List<String> list = Arrays.asList("a", "bb", "ccc");
        List<Integer> lengths = CollUtil.extract(list, String::length);
        assertEquals(Arrays.asList(1, 2, 3), lengths);
    }
}
