package com.github.leyland.letool.datastructure.tree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("树结构测试")
class TreeTest {

    // ---- 测试用实体 ----
    static class Dept implements TreeNode<Dept> {
        private Long id;
        private Long parentId;
        private String name;
        private List<Dept> children = new ArrayList<>();

        Dept(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        @Override public Object getId() { return id; }
        @Override public Object getParentId() { return parentId; }
        @Override public List<Dept> getChildren() { return children; }
        @Override public void setChildren(List<Dept> children) { this.children = children; }
        public String getName() { return name; }

        @Override public String toString() { return "Dept{id=" + id + ", name='" + name + "'}"; }
    }

    static List<Dept> sampleFlatList() {
        return Arrays.asList(
                new Dept(1L, null, "总公司"),
                new Dept(2L, 1L, "研发部"),
                new Dept(3L, 1L, "市场部"),
                new Dept(4L, 2L, "后端组"),
                new Dept(5L, 2L, "前端组"),
                new Dept(6L, 3L, "品牌组")
        );
    }

    @Nested
    @DisplayName("TreeNode 接口默认方法")
    class TreeNodeDefaults {

        @Test
        @DisplayName("parentId 为 null 时 isRoot 返回 true")
        void isRoot() {
            Dept root = new Dept(1L, null, "root");
            assertTrue(root.isRoot());
        }

        @Test
        @DisplayName("parentId 不为 null 时 isRoot 返回 false")
        void isNotRoot() {
            Dept child = new Dept(2L, 1L, "child");
            assertFalse(child.isRoot());
        }

        @Test
        @DisplayName("无子节点时 isLeaf 返回 true")
        void isLeaf() {
            Dept node = new Dept(1L, null, "leaf");
            assertTrue(node.isLeaf());
        }

        @Test
        @DisplayName("空子节点列表时 isLeaf 返回 true")
        void isLeafEmptyList() {
            Dept node = new Dept(1L, null, "leaf");
            node.setChildren(Collections.emptyList());
            assertTrue(node.isLeaf());
        }

        @Test
        @DisplayName("有子节点时 isLeaf 返回 false")
        void isNotLeaf() {
            Dept parent = new Dept(1L, null, "parent");
            parent.setChildren(Collections.singletonList(new Dept(2L, 1L, "child")));
            assertFalse(parent.isLeaf());
        }
    }

    @Nested
    @DisplayName("SimpleTreeNode")
    class SimpleTreeNodeTests {

        @Test
        @DisplayName("静态工厂方法创建节点")
        void of() {
            SimpleTreeNode<String> node = SimpleTreeNode.of(1, null, "root");
            assertEquals(1, node.getId());
            assertNull(node.getParentId());
            assertEquals("root", node.getData());
            assertTrue(node.isRoot());
        }

        @Test
        @DisplayName("addChild 链式添加子节点")
        void addChild() {
            SimpleTreeNode<String> root = SimpleTreeNode.of(1, null, "root");
            root.addChild(SimpleTreeNode.of(2, 1, "child1"))
                .addChild(SimpleTreeNode.of(3, 1, "child2"));

            assertEquals(2, root.getChildren().size());
            assertEquals("child1", root.getChildren().get(0).getData());
            assertEquals("child2", root.getChildren().get(1).getData());
        }

        @Test
        @DisplayName("addChildren 批量添加")
        void addChildren() {
            SimpleTreeNode<String> root = SimpleTreeNode.of(1, null, "root");
            root.addChildren(
                    SimpleTreeNode.of(2, 1, "a"),
                    SimpleTreeNode.of(3, 1, "b"),
                    SimpleTreeNode.of(4, 1, "c")
            );
            assertEquals(3, root.getChildren().size());
        }
    }

    @Nested
    @DisplayName("TreeBuilder 树构建")
    class TreeBuilderTests {

        @Test
        @DisplayName("空列表返回空集合")
        void emptyList() {
            assertTrue(TreeBuilder.build(Collections.<Dept>emptyList()).isEmpty());
            assertTrue(TreeBuilder.buildSimple(Collections.<String>emptyList(), s -> s, s -> null).isEmpty());
        }

        @Test
        @DisplayName("null 列表返回空集合")
        void nullList() {
            assertTrue(TreeBuilder.build(null).isEmpty());
        }

        @Test
        @DisplayName("从平列表构建树结构")
        void build() {
            List<Dept> flatList = sampleFlatList();
            List<Dept> roots = TreeBuilder.build(flatList);

            assertEquals(1, roots.size());
            Dept root = roots.get(0);
            assertEquals("总公司", root.getName());
            assertEquals(2, root.getChildren().size());

            Dept rd = root.getChildren().stream()
                    .filter(d -> "研发部".equals(d.getName())).findFirst().orElseThrow();
            assertEquals(2, rd.getChildren().size());
        }

        @Test
        @DisplayName("buildSimple 通过映射函数构建树")
        void buildSimple() {
            List<Dept> flatList = sampleFlatList();
            List<SimpleTreeNode<Dept>> roots = TreeBuilder.buildSimple(flatList, Dept::getId, Dept::getParentId);

            assertEquals(1, roots.size());
            SimpleTreeNode<Dept> root = roots.get(0);
            assertEquals("总公司", root.getData().getName());
            assertEquals(2, root.getChildren().size());
        }
    }

    @Nested
    @DisplayName("TreeUtil 遍历")
    class TreeTraversalTests {

        private Dept buildTree() {
            List<Dept> roots = TreeBuilder.build(sampleFlatList());
            return roots.get(0);
        }

        @Test
        @DisplayName("前序遍历——根 → 左 → 右顺序")
        void preOrder() {
            List<String> order = new ArrayList<>();
            TreeUtil.traversePreOrder(buildTree(), d -> order.add(d.getName()));

            assertEquals(Arrays.asList("总公司", "研发部", "后端组", "前端组", "市场部", "品牌组"), order);
        }

        @Test
        @DisplayName("后序遍历——子节点先于父节点")
        void postOrder() {
            List<String> order = new ArrayList<>();
            TreeUtil.traversePostOrder(buildTree(), d -> order.add(d.getName()));

            // 后端组, 前端组, 研发部, 品牌组, 市场部, 总公司
            assertEquals("总公司", order.get(order.size() - 1));
            assertTrue(order.indexOf("后端组") < order.indexOf("研发部"));
            assertTrue(order.indexOf("前端组") < order.indexOf("研发部"));
        }

        @Test
        @DisplayName("层序遍历——按层级从上到下")
        void levelOrder() {
            List<String> order = new ArrayList<>();
            TreeUtil.traverseLevelOrder(buildTree(), d -> order.add(d.getName()));

            assertEquals("总公司", order.get(0));
            assertTrue(order.indexOf("研发部") < order.indexOf("后端组"));
            assertTrue(order.indexOf("市场部") < order.indexOf("品牌组"));
        }

        @Test
        @DisplayName("null 节点不抛异常")
        void nullRoot() {
            assertDoesNotThrow(() -> TreeUtil.traversePreOrder(null, d -> {}));
            assertDoesNotThrow(() -> TreeUtil.traversePostOrder(null, d -> {}));
            assertDoesNotThrow(() -> TreeUtil.traverseLevelOrder(null, d -> {}));
        }

        @Test
        @DisplayName("toListPreOrder 收集为列表")
        void toListPreOrder() {
            List<Dept> list = TreeUtil.toListPreOrder(buildTree());
            assertEquals(6, list.size());
        }

        @Test
        @DisplayName("flatten 层序展平")
        void flatten() {
            List<Dept> list = TreeUtil.flatten(buildTree());
            assertEquals(6, list.size());
            assertEquals("总公司", list.get(0).getName());
        }
    }

    @Nested
    @DisplayName("TreeUtil 查询")
    class TreeQueryTests {

        private Dept buildTree() {
            return TreeBuilder.build(sampleFlatList()).get(0);
        }

        @Test
        @DisplayName("findFirst 找到匹配节点")
        void findFirst() {
            Optional<Dept> found = TreeUtil.findFirst(buildTree(), d -> "后端组".equals(d.getName()));
            assertTrue(found.isPresent());
            assertEquals(4L, found.get().getId());
        }

        @Test
        @DisplayName("findFirst 未找到返回空")
        void findFirstNotFound() {
            Optional<Dept> found = TreeUtil.findFirst(buildTree(), d -> "不存在的部门".equals(d.getName()));
            assertFalse(found.isPresent());
        }

        @Test
        @DisplayName("collectLeaves 收集所有叶子")
        void collectLeaves() {
            List<Dept> leaves = TreeUtil.collectLeaves(buildTree());
            assertEquals(3, leaves.size());
            assertTrue(leaves.stream().allMatch(Dept::isLeaf));
        }

        @Test
        @DisplayName("getAncestors 获取祖先链")
        void getAncestors() {
            List<Dept> flat = sampleFlatList();
            Dept backend = flat.stream().filter(d -> "后端组".equals(d.getName())).findFirst().orElseThrow();
            List<Dept> ancestors = TreeUtil.getAncestors(flat, backend);

            assertEquals(2, ancestors.size());
            assertEquals("总公司", ancestors.get(0).getName());
            assertEquals("研发部", ancestors.get(1).getName());
        }

        @Test
        @DisplayName("根节点 getAncestors 返回空列表")
        void getAncestorsRoot() {
            List<Dept> flat = sampleFlatList();
            Dept root = flat.stream().filter(d -> "总公司".equals(d.getName())).findFirst().orElseThrow();
            assertTrue(TreeUtil.getAncestors(flat, root).isEmpty());
        }
    }

    @Nested
    @DisplayName("TreeUtil 统计")
    class TreeStatsTests {

        private Dept buildTree() {
            return TreeBuilder.build(sampleFlatList()).get(0);
        }

        @Test
        @DisplayName("maxDepth 计算树深度")
        void maxDepth() {
            assertEquals(3, TreeUtil.maxDepth(buildTree()));
        }

        @Test
        @DisplayName("只有根节点深度为 1")
        void maxDepthSingleNode() {
            Dept root = new Dept(1L, null, "only");
            assertEquals(1, TreeUtil.maxDepth(root));
        }

        @Test
        @DisplayName("countNodes 统计节点总数")
        void countNodes() {
            assertEquals(6, TreeUtil.countNodes(buildTree()));
        }
    }
}
