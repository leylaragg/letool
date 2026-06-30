package com.github.leyland.letool.datastructure.tree;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 树工具 —— 提供树的遍历、查找、路径追踪等操作.
 *
 * <pre>{@code
 * // 深度优先遍历
 * TreeUtil.traversePreOrder(root, node -> System.out.println(node.getId()));
 *
 * // 广度优先遍历
 * TreeUtil.traverseLevelOrder(root, node -> process(node));
 *
 * // 查找第一个匹配节点
 * Optional<Dept> found = TreeUtil.findFirst(root, n -> "IT".equals(n.getName()));
 *
 * // 获取某个节点的所有祖先
 * List<Dept> ancestors = TreeUtil.getAncestors(flatList, dept);
 *
 * // 展开所有后代（BFS）
 * List<Dept> allDescendants = TreeUtil.flatten(root);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class TreeUtil {

    private TreeUtil() {}

    /** 前序遍历（深度优先）. */
    public static <T extends TreeNode<T>> void traversePreOrder(T root, Consumer<T> consumer) {
        if (root == null) return;
        Deque<T> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            consumer.accept(node);
            List<T> children = node.getChildren();
            if (children != null) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
    }

    /** 后序遍历（深度优先）. */
    public static <T extends TreeNode<T>> void traversePostOrder(T root, Consumer<T> consumer) {
        if (root == null) return;
        Deque<T> stack = new ArrayDeque<>();
        Deque<T> output = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            output.push(node);
            List<T> children = node.getChildren();
            if (children != null) {
                for (T child : children) {
                    stack.push(child);
                }
            }
        }
        while (!output.isEmpty()) {
            consumer.accept(output.pop());
        }
    }

    /** 层序遍历（广度优先）. */
    public static <T extends TreeNode<T>> void traverseLevelOrder(T root, Consumer<T> consumer) {
        if (root == null) return;
        Queue<T> queue = new ArrayDeque<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            T node = queue.poll();
            consumer.accept(node);
            List<T> children = node.getChildren();
            if (children != null) {
                children.forEach(queue::offer);
            }
        }
    }

    /** 前序遍历——收集为列表. */
    public static <T extends TreeNode<T>> List<T> toListPreOrder(T root) {
        List<T> result = new ArrayList<>();
        traversePreOrder(root, result::add);
        return result;
    }

    /** 层序遍历——收集为列表（平坦化树结构）. */
    public static <T extends TreeNode<T>> List<T> flatten(T root) {
        List<T> result = new ArrayList<>();
        traverseLevelOrder(root, result::add);
        return result;
    }

    /** 获得所有的叶子节点，深度优先遍历方式. */
    public static <T extends TreeNode<T>> List<T> collectLeaves(T root) {
        List<T> leaves = new ArrayList<>();
        traversePreOrder(root, node -> {
            if (node.isLeaf()) {
                leaves.add(node);
            }
        });
        return leaves;
    }

    /** 深度优先查找第一个匹配节点. */
    public static <T extends TreeNode<T>> Optional<T> findFirst(T root, Predicate<T> predicate) {
        if (root == null) return Optional.empty();
        Deque<T> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            if (predicate.test(node)) {
                return Optional.of(node);
            }
            List<T> children = node.getChildren();
            if (children != null) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 从平列表中查找某节点的所有祖先（从根到父节点）.
     *
     * @param flatList 完整的平铺节点列表
     * @param target   目标节点
     * @param <T>      节点类型
     * @return 祖先节点列表（从根到父节点，不含目标自身）
     */
    public static <T extends TreeNode<T>> List<T> getAncestors(List<T> flatList, T target) {
        if (flatList == null || target == null || target.getParentId() == null) {
            return Collections.emptyList();
        }
        Map<Object, T> idMap = new LinkedHashMap<>();
        for (T node : flatList) {
            idMap.put(node.getId(), node);
        }
        List<T> ancestors = new ArrayList<>();
        Object currentParentId = target.getParentId();
        while (currentParentId != null) {
            T parent = idMap.get(currentParentId);
            if (parent == null) break;
            ancestors.add(parent);
            currentParentId = parent.getParentId();
        }
        Collections.reverse(ancestors);
        return ancestors;
    }

    /** 计算树的最大深度. */
    public static <T extends TreeNode<T>> int maxDepth(T root) {
        if (root == null) return 0;
        int maxChildDepth = 0;
        List<T> children = root.getChildren();
        if (children != null) {
            for (T child : children) {
                maxChildDepth = Math.max(maxChildDepth, maxDepth(child));
            }
        }
        return 1 + maxChildDepth;
    }

    /** 计算节点总数（含 root）. */
    public static <T extends TreeNode<T>> int countNodes(T root) {
        if (root == null) return 0;
        int count = 1;
        List<T> children = root.getChildren();
        if (children != null) {
            for (T child : children) {
                count += countNodes(child);
            }
        }
        return count;
    }
}
