package com.github.leyland.letool.datastructure.tree;

import com.github.leyland.letool.datastructure.exception.DataStructureErrorCode;
import com.github.leyland.letool.datastructure.exception.DataStructureException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树构建和树操作的关键生产契约测试。
 */
class TreeTest {

    /**
     * 验证构建结果保持输入顺序，并为每个节点回填独立、可修改的子节点列表。
     */
    @Test
    void shouldBuildOrderedMutableTree() {
        List<Dept> flatList = sampleFlatList();

        List<Dept> roots = TreeBuilder.build(flatList);

        assertEquals(List.of("总公司"), names(roots));
        assertEquals(List.of("研发部", "市场部"), names(roots.get(0).getChildren()));
        assertEquals(List.of("后端组", "前端组"), names(roots.get(0).getChildren().get(0).getChildren()));
        roots.get(0).getChildren().get(1).getChildren().add(new Dept(7L, 3L, "增长组"));
        assertEquals(2, roots.get(0).getChildren().get(1).childCount());
    }

    /**
     * 验证重复 ID 被稳定拒绝，并且失败前不会覆盖调用方已有子节点。
     */
    @Test
    void shouldRejectDuplicateIdWithoutMutatingInput() {
        Dept sentinel = new Dept(99L, 1L, "原子性哨兵");
        Dept first = new Dept(1L, null, "第一个节点");
        List<Dept> originalChildren = new ArrayList<>(List.of(sentinel));
        first.setChildren(originalChildren);
        Dept duplicate = new Dept(1L, null, "重复节点");

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> TreeBuilder.build(List.of(first, duplicate))
        );

        assertEquals(DataStructureErrorCode.DUPLICATE_TREE_ID, exception.getErrorCode());
        assertSame(originalChildren, first.getChildren());
    }

    /**
     * 验证孤儿节点默认失败，显式降级时提升为根且仍能挂载自己的后代。
     */
    @Test
    void shouldHandleOrphanOnlyWithExplicitPolicy() {
        Dept root = new Dept(1L, null, "正常根");
        Dept orphan = new Dept(2L, 99L, "孤儿根");
        Dept child = new Dept(3L, 2L, "孤儿后代");
        List<Dept> flatList = List.of(root, orphan, child);

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> TreeBuilder.build(flatList)
        );
        assertEquals(DataStructureErrorCode.ORPHAN_TREE_NODE, exception.getErrorCode());

        List<Dept> roots = TreeBuilder.build(flatList, TreeOrphanPolicy.AS_ROOT);
        assertEquals(List.of("正常根", "孤儿根"), names(roots));
        assertEquals(List.of("孤儿后代"), names(orphan.getChildren()));
    }

    /**
     * 验证父链环在回填子节点之前被拒绝。
     */
    @Test
    void shouldRejectParentCycleWithoutMutatingInput() {
        Dept first = new Dept(1L, 2L, "节点一");
        Dept second = new Dept(2L, 1L, "节点二");
        List<Dept> originalChildren = new ArrayList<>();
        first.setChildren(originalChildren);

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> TreeBuilder.build(List.of(first, second), TreeOrphanPolicy.AS_ROOT)
        );

        assertEquals(DataStructureErrorCode.INVALID_TREE_STRUCTURE, exception.getErrorCode());
        assertSame(originalChildren, first.getChildren());
    }

    /**
     * 验证普通实体可以通过映射函数包装成树，并遵循相同的严格校验。
     */
    @Test
    void shouldBuildSimpleTreeWithSameValidationContract() {
        List<Dept> flatList = sampleFlatList();

        List<SimpleTreeNode<Dept>> roots = TreeBuilder.buildSimple(
                flatList,
                Dept::getId,
                Dept::getParentId
        );

        assertEquals("总公司", roots.get(0).getData().getName());
        assertEquals(2, roots.get(0).childCount());
        assertError(
                DataStructureErrorCode.INVALID_ARGUMENT,
                () -> TreeBuilder.buildSimple(flatList, null, Dept::getParentId)
        );
    }

    /**
     * 验证核心遍历、查询和统计保持约定顺序。
     */
    @Test
    void shouldTraverseQueryAndMeasureTree() {
        Dept root = TreeBuilder.build(sampleFlatList()).get(0);
        List<String> postOrder = new ArrayList<>();
        TreeUtil.traversePostOrder(root, node -> postOrder.add(node.getName()));

        assertEquals(
                List.of("总公司", "研发部", "后端组", "前端组", "市场部", "品牌组"),
                names(TreeUtil.toListPreOrder(root))
        );
        assertEquals(
                List.of("总公司", "研发部", "市场部", "后端组", "前端组", "品牌组"),
                names(TreeUtil.flatten(root))
        );
        assertEquals(List.of("后端组", "前端组", "研发部", "品牌组", "市场部", "总公司"), postOrder);
        assertEquals(List.of("后端组", "前端组", "品牌组"), names(TreeUtil.collectLeaves(root)));
        assertEquals(Optional.of("前端组"), TreeUtil.findFirst(root, node -> node.getId().equals(5L)).map(Dept::getName));
        assertEquals(3, TreeUtil.maxDepth(root));
        assertEquals(6, TreeUtil.countNodes(root));
    }

    /**
     * 验证万级深树不会因递归实现造成线程栈溢出。
     */
    @Test
    void shouldHandleDeepTreeWithoutRecursion() {
        int depth = 10_000;
        Dept root = new Dept(0L, null, "0");
        Dept current = root;
        for (long index = 1L; index < depth; index++) {
            Dept child = new Dept(index, index - 1L, String.valueOf(index));
            current.setChildren(List.of(child));
            current = child;
        }

        assertEquals(depth, TreeUtil.countNodes(root));
        assertEquals(depth, TreeUtil.maxDepth(root));
        assertEquals(depth, TreeUtil.toListPreOrder(root).size());
    }

    /**
     * 验证遍历遇到环或重复对象引用时快速失败，而不是无限执行。
     */
    @Test
    void shouldRejectInvalidRuntimeTreeTopology() {
        Dept root = new Dept(1L, null, "根");
        Dept child = new Dept(2L, 1L, "子");
        root.setChildren(Arrays.asList(child, child));

        assertError(DataStructureErrorCode.INVALID_TREE_STRUCTURE, () -> TreeUtil.countNodes(root));

        child.setChildren(List.of(root));
        root.setChildren(List.of(child));
        assertError(DataStructureErrorCode.INVALID_TREE_STRUCTURE, () -> TreeUtil.flatten(root));
    }

    /**
     * 验证祖先查询不会静默接受重复 ID、缺失父节点或父链环。
     */
    @Test
    void shouldRejectInvalidAncestorIndex() {
        Dept target = new Dept(3L, 2L, "目标");

        assertError(
                DataStructureErrorCode.DUPLICATE_TREE_ID,
                () -> TreeUtil.getAncestors(List.of(new Dept(1L, null, "一"), new Dept(1L, null, "重复")), target)
        );
        assertError(
                DataStructureErrorCode.ORPHAN_TREE_NODE,
                () -> TreeUtil.getAncestors(List.of(target), target)
        );
        assertError(
                DataStructureErrorCode.INVALID_TREE_STRUCTURE,
                () -> TreeUtil.getAncestors(
                        List.of(new Dept(1L, 2L, "一"), new Dept(2L, 1L, "二"), target),
                        target
                )
        );
    }

    /**
     * 断言操作抛出指定稳定错误码。
     *
     * @param errorCode 预期错误码
     * @param action 待执行操作
     */
    private static void assertError(DataStructureErrorCode errorCode, Runnable action) {
        DataStructureException exception = assertThrows(DataStructureException.class, action::run);
        assertEquals(errorCode, exception.getErrorCode());
    }

    /**
     * 创建保持确定顺序的部门平列表。
     *
     * @return 部门平列表
     */
    private static List<Dept> sampleFlatList() {
        return List.of(
                new Dept(1L, null, "总公司"),
                new Dept(2L, 1L, "研发部"),
                new Dept(3L, 1L, "市场部"),
                new Dept(4L, 2L, "后端组"),
                new Dept(5L, 2L, "前端组"),
                new Dept(6L, 3L, "品牌组")
        );
    }

    /**
     * 提取节点名称，便于断言遍历顺序。
     *
     * @param nodes 节点列表
     * @return 名称列表
     */
    private static List<String> names(List<Dept> nodes) {
        return nodes.stream().map(Dept::getName).toList();
    }

    /**
     * 测试使用的部门节点。
     */
    private static final class Dept implements TreeNode<Dept> {

        /** 节点 ID。 */
        private final Long id;

        /** 父节点 ID。 */
        private final Long parentId;

        /** 部门名称。 */
        private final String name;

        /** 子部门列表。 */
        private List<Dept> children = new ArrayList<>();

        /**
         * 创建部门节点。
         *
         * @param id 节点 ID
         * @param parentId 父节点 ID
         * @param name 部门名称
         */
        private Dept(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        /** {@inheritDoc} */
        @Override
        public Object getId() {
            return id;
        }

        /** {@inheritDoc} */
        @Override
        public Object getParentId() {
            return parentId;
        }

        /** {@inheritDoc} */
        @Override
        public List<Dept> getChildren() {
            return children;
        }

        /** {@inheritDoc} */
        @Override
        public void setChildren(List<Dept> children) {
            this.children = children;
        }

        /**
         * 获取部门名称。
         *
         * @return 部门名称
         */
        private String getName() {
            return name;
        }
    }
}
