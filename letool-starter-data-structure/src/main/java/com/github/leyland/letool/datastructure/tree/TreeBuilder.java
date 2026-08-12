package com.github.leyland.letool.datastructure.tree;

import com.github.leyland.letool.datastructure.exception.DataStructureException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 将数据库等来源的平铺节点列表构建为有序树结构。
 *
 * <p>构建器先完成空值、重复 ID、孤儿节点和父链环校验，再统一回填子节点列表。
 * 因此任何结构校验失败都不会留下只回填了一部分节点的半成品树。</p>
 *
 * <p>构建过程会调用 {@link TreeNode#setChildren(List)} 修改传入节点。业务实体不适合被修改时，
 * 应使用 {@link #buildSimple(List, Function, Function)} 创建包装节点。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class TreeBuilder {

    /**
     * 工具类不允许实例化。
     */
    private TreeBuilder() {
    }

    /**
     * 使用严格孤儿策略从平列表构建树。
     *
     * @param flatList 平铺节点列表；为空引用或空列表时返回空列表
     * @param <T> 节点类型
     * @return 按输入顺序排列的根节点列表
     * @throws DataStructureException 当节点、ID、父子关系或拓扑不符合契约时抛出
     */
    public static <T extends TreeNode<T>> List<T> build(List<T> flatList) {
        return build(flatList, TreeOrphanPolicy.REJECT);
    }

    /**
     * 使用指定孤儿策略从平列表构建树。
     *
     * @param flatList 平铺节点列表；为空引用或空列表时返回空列表
     * @param orphanPolicy 孤儿节点处理策略
     * @param <T> 节点类型
     * @return 按输入顺序排列的根节点列表
     * @throws DataStructureException 当策略、节点、ID、父子关系或拓扑不符合契约时抛出
     */
    public static <T extends TreeNode<T>> List<T> build(
            List<T> flatList,
            TreeOrphanPolicy orphanPolicy) {
        if (orphanPolicy == null) {
            throw DataStructureException.invalidArgument("orphanPolicy");
        }
        if (flatList == null || flatList.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Object, T> idIndex = createIdIndex(flatList);
        validateParentRelations(idIndex, orphanPolicy);
        return assemble(flatList, idIndex, orphanPolicy);
    }

    /**
     * 使用映射函数把普通实体包装为简单树节点，并采用严格孤儿策略构建树。
     *
     * @param flatList 平铺实体列表；为空引用或空列表时返回空列表
     * @param idMapper 节点 ID 提取函数
     * @param parentIdMapper 父节点 ID 提取函数
     * @param <T> 实体类型
     * @return 简单树节点根列表
     * @throws DataStructureException 当映射函数或映射结果不符合契约时抛出
     */
    public static <T> List<SimpleTreeNode<T>> buildSimple(
            List<T> flatList,
            Function<T, Object> idMapper,
            Function<T, Object> parentIdMapper) {
        return buildSimple(flatList, idMapper, parentIdMapper, TreeOrphanPolicy.REJECT);
    }

    /**
     * 使用映射函数把普通实体包装为简单树节点，并采用指定孤儿策略构建树。
     *
     * @param flatList 平铺实体列表；为空引用或空列表时返回空列表
     * @param idMapper 节点 ID 提取函数
     * @param parentIdMapper 父节点 ID 提取函数
     * @param orphanPolicy 孤儿节点处理策略
     * @param <T> 实体类型
     * @return 简单树节点根列表
     * @throws DataStructureException 当映射函数、策略或映射结果不符合契约时抛出
     */
    public static <T> List<SimpleTreeNode<T>> buildSimple(
            List<T> flatList,
            Function<T, Object> idMapper,
            Function<T, Object> parentIdMapper,
            TreeOrphanPolicy orphanPolicy) {
        if (idMapper == null) {
            throw DataStructureException.invalidArgument("idMapper");
        }
        if (parentIdMapper == null) {
            throw DataStructureException.invalidArgument("parentIdMapper");
        }
        if (orphanPolicy == null) {
            throw DataStructureException.invalidArgument("orphanPolicy");
        }
        if (flatList == null || flatList.isEmpty()) {
            return new ArrayList<>();
        }

        List<SimpleTreeNode<T>> nodes = new ArrayList<>(flatList.size());
        for (T element : flatList) {
            if (element == null) {
                throw DataStructureException.invalidArgument("flatListElement");
            }
            nodes.add(new SimpleTreeNode<>(
                    idMapper.apply(element),
                    parentIdMapper.apply(element),
                    element
            ));
        }
        return build(nodes, orphanPolicy);
    }

    /**
     * 创建保持输入顺序的节点 ID 索引。
     *
     * @param flatList 平铺节点列表
     * @param <T> 节点类型
     * @return 节点 ID 索引
     */
    private static <T extends TreeNode<T>> Map<Object, T> createIdIndex(List<T> flatList) {
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
        return idIndex;
    }

    /**
     * 校验孤儿节点和父链环。
     *
     * @param idIndex 节点 ID 索引
     * @param orphanPolicy 孤儿节点处理策略
     * @param <T> 节点类型
     */
    private static <T extends TreeNode<T>> void validateParentRelations(
            Map<Object, T> idIndex,
            TreeOrphanPolicy orphanPolicy) {
        if (orphanPolicy == TreeOrphanPolicy.REJECT) {
            for (T node : idIndex.values()) {
                Object parentId = node.getParentId();
                if (parentId != null && !idIndex.containsKey(parentId)) {
                    throw DataStructureException.orphanTreeNode();
                }
            }
        }

        Set<Object> resolved = new HashSet<>();
        for (Object nodeId : idIndex.keySet()) {
            if (resolved.contains(nodeId)) {
                continue;
            }
            Set<Object> path = new HashSet<>();
            Object currentId = nodeId;
            while (currentId != null && idIndex.containsKey(currentId) && !resolved.contains(currentId)) {
                if (!path.add(currentId)) {
                    throw DataStructureException.invalidTreeStructure();
                }
                currentId = idIndex.get(currentId).getParentId();
            }
            resolved.addAll(path);
        }
    }

    /**
     * 在结构校验通过后统一回填子节点并收集根节点。
     *
     * @param flatList 平铺节点列表
     * @param idIndex 节点 ID 索引
     * @param orphanPolicy 孤儿节点处理策略
     * @param <T> 节点类型
     * @return 根节点列表
     */
    private static <T extends TreeNode<T>> List<T> assemble(
            List<T> flatList,
            Map<Object, T> idIndex,
            TreeOrphanPolicy orphanPolicy) {
        Map<Object, List<T>> childIndex = new LinkedHashMap<>();
        List<T> roots = new ArrayList<>();
        for (T node : flatList) {
            Object parentId = node.getParentId();
            boolean orphanAsRoot = orphanPolicy == TreeOrphanPolicy.AS_ROOT
                    && parentId != null
                    && !idIndex.containsKey(parentId);
            if (parentId == null || orphanAsRoot) {
                roots.add(node);
            } else {
                childIndex.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node);
            }
        }

        for (T node : flatList) {
            List<T> children = childIndex.get(node.getId());
            node.setChildren(children == null ? new ArrayList<>() : new ArrayList<>(children));
        }
        return roots;
    }
}
