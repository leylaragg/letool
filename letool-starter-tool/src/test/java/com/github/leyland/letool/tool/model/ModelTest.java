package com.github.leyland.letool.tool.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void rOk() {
        R<String> r = R.ok("hello");
        assertTrue(r.isSuccess());
        assertEquals("00000", r.getCode());
        assertEquals("hello", r.getData());
        assertTrue(r.getTimestamp() > 0);
    }

    @Test
    void rFail() {
        R<String> r = R.fail("A0001", "参数错误");
        assertFalse(r.isSuccess());
        assertEquals("A0001", r.getCode());
        assertEquals("参数错误", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void pageResult() {
        PageResult<String> page = PageResult.of(
                Arrays.asList("a", "b", "c"), 30, 1, 10);
        assertEquals(3, page.getRecords().size());
        assertEquals(30, page.getTotal());
        assertEquals(3, page.getTotalPages());
    }

    @Test
    void pageResultEmpty() {
        PageResult<String> page = PageResult.empty(1, 10);
        assertTrue(page.getRecords().isEmpty());
        assertEquals(0, page.getTotal());
    }

    @Test
    void pageResultMap() {
        PageResult<Integer> page = PageResult.of(
                Arrays.asList(1, 2, 3), 3, 1, 10);
        PageResult<String> mapped = page.map(String::valueOf);
        assertEquals("1", mapped.getRecords().get(0));
    }

    @Test
    void treeNode() {
        TreeNode<String> root = TreeNode.of("1", "0", "root", "data1");
        assertTrue(root.isRoot());
        TreeNode<String> child = TreeNode.of("2", "1", "child", "data2");
        root.addChild(child);
        assertFalse(root.isLeaf());
        assertTrue(child.isLeaf());
        assertEquals(1, root.getChildren().size());
    }
}
