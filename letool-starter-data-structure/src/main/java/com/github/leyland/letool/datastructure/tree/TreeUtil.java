package com.github.leyland.letool.datastructure.tree;

import com.github.leyland.letool.datastructure.exception.DataStructureException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 提供身份安全、非递归的树遍历、查询和统计能力。
 *
 * <p>所有遍历都按节点对象身份检测重复访问。同一对象被多个父节点引用或形成环时，
 * 将抛出稳定的数据结构异常，避免无限循环。算法不使用递归，可以处理万级深树。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class TreeUtil {

    /**
     * 工具类不允许实例化。
     */
    private TreeUtil() {
    }

    /**
     * 按“根、从左到右的子树”执行前序遍历。
     *
     * @param root 根节点；为空时执行空操作
     * @param consumer 节点处理器
     * @param <T> 节点类型
     * @throws DataStructureException 当处理器为空或树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> void traversePreOrder(T root, Consumer<T> consumer) {
        requireConsumer(consumer);
        if (root == null) {
            return;
        }

        Set<T> visited = identitySet();
        Deque<T> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            markVisited(visited, node);
            consumer.accept(node);

            List<T> children = childrenOf(node);
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
    }

    /**
     * 按“从左到右的子树、根”执行后序遍历。
     *
     * <p>在调用处理器前会先验证整个可达树，避免发现非法拓扑时只处理了一半节点。</p>
     *
     * @param root 根节点；为空时执行空操作
     * @param consumer 节点处理器
     * @param <T> 节点类型
     * @throws DataStructureException 当处理器为空或树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> void traversePostOrder(T root, Consumer<T> consumer) {
        requireConsumer(consumer);
        if (root == null) {
            return;
        }

        Set<T> visited = identitySet();
        Deque<T> pending = new ArrayDeque<>();
        Deque<T> output = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            T node = pending.pop();
            markVisited(visited, node);
            output.push(node);
            for (T child : childrenOf(node)) {
                pending.push(child);
            }
        }
        while (!output.isEmpty()) {
            consumer.accept(output.pop());
        }
    }

    /**
     * 按从上到下、同层从左到右执行层序遍历。
     *
     * @param root 根节点；为空时执行空操作
     * @param consumer 节点处理器
     * @param <T> 节点类型
     * @throws DataStructureException 当处理器为空或树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> void traverseLevelOrder(T root, Consumer<T> consumer) {
        requireConsumer(consumer);
        if (root == null) {
            return;
        }

        Set<T> visited = identitySet();
        Queue<T> queue = new ArrayDeque<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            T node = queue.remove();
            markVisited(visited, node);
            consumer.accept(node);
            for (T child : childrenOf(node)) {
                queue.offer(child);
            }
        }
    }

    /**
     * 按前序遍历顺序收集树中节点。
     *
     * @param root 根节点；为空时返回空列表
     * @param <T> 节点类型
     * @return 可修改的前序节点列表
     * @throws DataStructureException 当树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> List<T> toListPreOrder(T root) {
        List<T> result = new ArrayList<>();
        traversePreOrder(root, result::add);
        return result;
    }

    /**
     * 按层序遍历顺序把树展平为列表。
     *
     * @param root 根节点；为空时返回空列表
     * @param <T> 节点类型
     * @return 可修改的层序节点列表
     * @throws DataStructureException 当树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> List<T> flatten(T root) {
        List<T> result = new ArrayList<>();
        traverseLevelOrder(root, result::add);
        return result;
    }

    /**
     * 按前序遍历顺序收集所有叶子节点。
     *
     * @param root 根节点；为空时返回空列表
     * @param <T> 节点类型
     * @return 可修改的叶子节点列表
     * @throws DataStructureException 当树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> List<T> collectLeaves(T root) {
        List<T> leaves = new ArrayList<>();
        traversePreOrder(root, node -> {
            if (node.isLeaf()) {
                leaves.add(node);
            }
        });
        return leaves;
    }

    /**
     * 按前序顺序查找第一个满足条件的节点。
     *
     * @param root 根节点；为空时返回空结果
     * @param predicate 节点匹配条件
     * @param <T> 节点类型
     * @return 第一个匹配节点；没有匹配时为空
     * @throws DataStructureException 当匹配条件为空或已遍历拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> Optional<T> findFirst(T root, Predicate<T> predicate) {
        if (predicate == null) {
            throw DataStructureException.invalidArgument("predicate");
        }
        if (root == null) {
            return Optional.empty();
        }

        Set<T> visited = identitySet();
        Deque<T> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            markVisited(visited, node);
            if (predicate.test(node)) {
                return Optional.of(node);
            }
            List<T> children = childrenOf(node);
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return Optional.empty();
    }

    /**
     * 从平列表追踪目标节点的祖先，并按根到直接父节点的顺序返回。
     *
     * @param flatList 完整平铺节点列表；为空时返回空列表
     * @param target 目标节点；为空或本身为根时返回空列表
     * @param <T> 节点类型
     * @return 可修改的祖先节点列表，不包含目标自身
     * @throws DataStructureException 当节点 ID 重复、父节点缺失或父链成环时抛出
     */
    public static <T extends TreeNode<T>> List<T> getAncestors(List<T> flatList, T target) {
        if (flatList == null || flatList.isEmpty() || target == null || target.getParentId() == null) {
            return new ArrayList<>();
        }

        Map<Object, T> idIndex = new LinkedHashMap<>(flatList.size());
        for (T node : flatList) {
            if (node == null) {
                throw DataStructureException.invalidArgument("flatListElement");
            }
            Object nodeId = node.getId();
            if (nodeId == null) {
                throw DataStructureException.invalidArgument("nodeId");
            }
            if (idIndex.putIfAbsent(nodeId, node) != null) {
                throw DataStructureException.duplicateTreeId();
            }
        }

        Set<Object> visitedIds = new HashSet<>();
        List<T> ancestors = new ArrayList<>();
        Object currentParentId = target.getParentId();
        while (currentParentId != null) {
            if (!visitedIds.add(currentParentId)) {
                throw DataStructureException.invalidTreeStructure();
            }
            T parent = idIndex.get(currentParentId);
            if (parent == null) {
                throw DataStructureException.orphanTreeNode();
            }
            ancestors.add(parent);
            currentParentId = parent.getParentId();
        }
        Collections.reverse(ancestors);
        return ancestors;
    }

    /**
     * 计算树的最大深度，根节点深度为一。
     *
     * @param root 根节点；为空时返回零
     * @param <T> 节点类型
     * @return 最大深度
     * @throws DataStructureException 当树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> int maxDepth(T root) {
        if (root == null) {
            return 0;
        }

        int maxDepth = 0;
        Set<T> visited = identitySet();
        Queue<NodeDepth<T>> queue = new ArrayDeque<>();
        queue.offer(new NodeDepth<>(root, 1));
        while (!queue.isEmpty()) {
            NodeDepth<T> current = queue.remove();
            markVisited(visited, current.node());
            maxDepth = Math.max(maxDepth, current.depth());
            for (T child : childrenOf(current.node())) {
                queue.offer(new NodeDepth<>(child, current.depth() + 1));
            }
        }
        return maxDepth;
    }

    /**
     * 统计根节点及其所有后代的节点总数。
     *
     * @param root 根节点；为空时返回零
     * @param <T> 节点类型
     * @return 节点总数
     * @throws DataStructureException 当树拓扑无效时抛出
     */
    public static <T extends TreeNode<T>> int countNodes(T root) {
        if (root == null) {
            return 0;
        }
        int[] count = {0};
        traversePreOrder(root, ignored -> count[0]++);
        return count[0];
    }

    /**
     * 获取节点的子节点列表，并拒绝空子节点元素。
     *
     * @param node 当前节点
     * @param <T> 节点类型
     * @return 非空子节点列表
     */
    private static <T extends TreeNode<T>> List<T> childrenOf(T node) {
        List<T> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        for (T child : children) {
            if (child == null) {
                throw DataStructureException.invalidTreeStructure();
            }
        }
        return children;
    }

    /**
     * 标记已访问节点，重复出现时拒绝当前拓扑。
     *
     * @param visited 已访问节点集合
     * @param node 当前节点
     * @param <T> 节点类型
     */
    private static <T extends TreeNode<T>> void markVisited(Set<T> visited, T node) {
        if (node == null || !visited.add(node)) {
            throw DataStructureException.invalidTreeStructure();
        }
    }

    /**
     * 校验节点处理器。
     *
     * @param consumer 节点处理器
     * @param <T> 节点类型
     */
    private static <T> void requireConsumer(Consumer<T> consumer) {
        if (consumer == null) {
            throw DataStructureException.invalidArgument("consumer");
        }
    }

    /**
     * 创建按对象身份比较的集合。
     *
     * @param <T> 元素类型
     * @return 身份集合
     */
    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * 保存层序深度计算中的节点和深度。
     *
     * @param node 当前节点
     * @param depth 当前节点深度
     * @param <T> 节点类型
     */
    private record NodeDepth<T>(T node, int depth) {
    }
}
