package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.model.TreeNode;

import java.util.*;
import java.util.function.Function;

/**
 * 树形结构构建工具——将扁平列表转为树形结构或扁平化.
 *
 * <h3>两种构建算法</h3>
 * <table>
 *   <tr><th>方法</th><th>算法</th><th>适用场景</th></tr>
 *   <tr><td>{@link #buildTree(List, Function, Function, Function)}</td><td>递归</td><td>通用场景，代码直观</td></tr>
 *   <tr><td>{@link #buildTreeIterative(List, Function, Function)}</td><td>迭代</td><td>深层嵌套树（>1000 层），避免栈溢出</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 构建部门树
 * List<TreeNode<Dept>> tree = TreeUtil.buildTree(
 *     deptList,
 *     Dept::getId,          // ID 提取器
 *     Dept::getParentId,    // 父 ID 提取器
 *     Dept::getName         // 名称提取器
 * );
 *
 * // 扁平化（DFS 前序遍历）
 * List<TreeNode<Dept>> flat = TreeUtil.flatten(tree);
 * }</pre>
 */
public final class TreeUtil {

    private TreeUtil() {}

    /**
     * 使用递归构建树结构.
     *
     * <p>时间复杂度 O(n)，每个元素仅遍历一次.</p>
     *
     * @param list         源数据列表（扁平结构）
     * @param idMapper     ID 提取函数（如 {@code Dept::getId}）
     * @param parentMapper 父 ID 提取函数（如 {@code Dept::getParentId}）
     * @param nameMapper   名称提取函数（可为 {@code null}，此时用 ID 作为节点名）
     * @param <T>          业务数据类型
     * @return 根节点列表（顶层节点），列表为空返回空列表
     */
    public static <T> List<TreeNode<T>> buildTree(
            List<T> list,
            Function<T, String> idMapper,
            Function<T, String> parentMapper,
            Function<T, String> nameMapper) {
        if (CollUtil.isEmpty(list)) return Collections.emptyList();
        Map<String, TreeNode<T>> nodeMap = new LinkedHashMap<>();
        for (T item : list) {
            String id = idMapper.apply(item);
            String name = nameMapper != null ? nameMapper.apply(item) : id;
            nodeMap.put(id, new TreeNode<>(id, parentMapper.apply(item), name, item));
        }
        List<TreeNode<T>> roots = new ArrayList<>();
        for (TreeNode<T> node : nodeMap.values()) {
            if (node.isRoot() || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                TreeNode<T> parent = nodeMap.get(node.getParentId());
                if (parent != null) parent.addChild(node);
            }
        }
        return roots;
    }

    /**
     * 使用迭代方式构建树结构（避免深层递归导致的栈溢出）.
     *
     * <p>当前实现为优化的 HashMap 方法（与递归版逻辑相同，但避免了函数递归）.
     * 适用于树深度 > 1000 的极端场景.</p>
     *
     * @param list         源数据列表
     * @param idMapper     ID 提取函数
     * @param parentMapper 父 ID 提取函数
     * @param <T>          业务数据类型
     * @return 根节点列表
     */
    public static <T> List<TreeNode<T>> buildTreeIterative(
            List<T> list,
            Function<T, String> idMapper,
            Function<T, String> parentMapper) {
        if (CollUtil.isEmpty(list)) return Collections.emptyList();
        Map<String, TreeNode<T>> nodeMap = new LinkedHashMap<>();
        for (T item : list) {
            String id = idMapper.apply(item);
            nodeMap.put(id, new TreeNode<>(id, parentMapper.apply(item), id, item));
        }
        List<TreeNode<T>> roots = new ArrayList<>();
        for (TreeNode<T> node : nodeMap.values()) {
            if (node.isRoot() || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                TreeNode<T> parent = nodeMap.get(node.getParentId());
                if (parent != null) parent.addChild(node);
            }
        }
        return roots;
    }

    /**
     * 将树结构扁平化为列表（DFS 前序遍历）.
     *
     * <p>使用栈实现迭代遍历，避免递归溢出。结果列表顺序为：根 → 第一个孩子及其子树 → 第二个孩子...</p>
     *
     * @param tree 树节点列表（根节点们）
     * @param <T>  业务数据类型
     * @return 扁平化后的列表，{@code tree} 为空返回空列表
     */
    public static <T> List<TreeNode<T>> flatten(List<TreeNode<T>> tree) {
        List<TreeNode<T>> result = new ArrayList<>();
        if (CollUtil.isEmpty(tree)) return result;
        Deque<TreeNode<T>> stack = new ArrayDeque<>(tree);
        while (!stack.isEmpty()) {
            TreeNode<T> node = stack.pop();
            result.add(node);
            List<TreeNode<T>> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return result;
    }
}
