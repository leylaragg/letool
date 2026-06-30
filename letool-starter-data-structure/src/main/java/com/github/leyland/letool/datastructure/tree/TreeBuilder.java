package com.github.leyland.letool.datastructure.tree;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 树构建器 —— 从平列表（如数据库查询结果）构建树结构.
 *
 * <pre>{@code
 * // 方式一：实体实现 TreeNode 接口
 * List<Dept> tree = TreeBuilder.build(deptList);
 *
 * // 方式二：使用 SimpleTreeNode 包装
 * List<SimpleTreeNode<Dept>> tree = TreeBuilder.buildSimple(deptList,
 *     Dept::getId, Dept::getParentId);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class TreeBuilder {

    private TreeBuilder() {}

    /**
     * 从实现了 {@link TreeNode} 接口的实体平列表构建树，返回根节点列表.
     *
     * @param flatList 平铺的节点列表（通常是全表查询结果）
     * @param <T>      节点类型
     * @return 根节点列表（parentId 为 null 的节点）
     */
    public static <T extends TreeNode<T>> List<T> build(List<T> flatList) {
        if (flatList == null || flatList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Object, List<T>> parentIndex = flatList.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(TreeNode::getParentId, LinkedHashMap::new, Collectors.toList()));

        List<T> roots = new ArrayList<>();
        Map<Object, T> idMap = flatList.stream()
                .collect(Collectors.toMap(TreeNode::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        for (T node : flatList) {
            List<T> children = parentIndex.getOrDefault(node.getId(), Collections.emptyList());
            node.setChildren(children);
            if (node.isRoot()) {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 从普通实体平列表构建 {@link SimpleTreeNode} 树，通过传入 id/parentId 映射函数指定父子关系.
     *
     * @param flatList      平铺数据列表
     * @param idMapper      从实体提取 ID 的函数
     * @param parentIdMapper 从实体提取父 ID 的函数
     * @param <T>           实体类型
     * @return 根节点列表
     */
    public static <T> List<SimpleTreeNode<T>> buildSimple(List<T> flatList,
                                                           Function<T, Object> idMapper,
                                                           Function<T, Object> parentIdMapper) {
        if (flatList == null || flatList.isEmpty()) {
            return Collections.emptyList();
        }
        List<SimpleTreeNode<T>> nodes = flatList.stream()
                .map(e -> new SimpleTreeNode<>(idMapper.apply(e), parentIdMapper.apply(e), e))
                .collect(Collectors.toList());
        return build(nodes);
    }
}
