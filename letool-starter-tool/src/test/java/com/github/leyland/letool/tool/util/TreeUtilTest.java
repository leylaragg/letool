package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.model.TreeNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TreeUtilTest {

    static class Dept {
        String id, parentId, name;
        Dept(String id, String parentId, String name) { this.id = id; this.parentId = parentId; this.name = name; }
    }

    @Test
    void buildTree() {
        List<Dept> depts = Arrays.asList(
                new Dept("1", "0", "总公司"),
                new Dept("2", "1", "研发部"),
                new Dept("3", "1", "市场部"),
                new Dept("4", "2", "后端组")
        );
        List<TreeNode<Dept>> tree = TreeUtil.buildTree(depts,
                d -> d.id, d -> d.parentId, d -> d.name);
        assertEquals(1, tree.size());
        assertEquals("总公司", tree.get(0).getName());
        assertEquals(2, tree.get(0).getChildren().size());
        assertEquals("后端组", tree.get(0).getChildren().get(0).getChildren().get(0).getName());
    }

    @Test
    void flatten() {
        List<Dept> depts = Arrays.asList(
                new Dept("1", "0", "root"),
                new Dept("2", "1", "child")
        );
        List<TreeNode<Dept>> tree = TreeUtil.buildTree(depts,
                d -> d.id, d -> d.parentId, d -> d.name);
        List<TreeNode<Dept>> flat = TreeUtil.flatten(tree);
        assertEquals(2, flat.size());
    }

    @Test
    void emptyTree() {
        List<TreeNode<Object>> tree = TreeUtil.buildTree(
                java.util.Collections.emptyList(), Object::toString, Object::toString, null);
        assertTrue(tree.isEmpty());
    }
}
