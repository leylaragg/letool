package com.github.leyland.letool.datastructure.tree;

import java.util.List;

/**
 * 泛型树节点接口 —— 采用 CRTP 自引用泛型模式，用户实体实现此接口即可被 {@link TreeBuilder} 和
 * {@link TreeUtil} 识别并构建树结构.
 *
 * <p>典型用法：业务实体直接实现此接口：</p>
 * <pre>{@code
 * public class Dept implements TreeNode<Dept> {
 *     private Long id;
 *     private Long parentId;
 *     private List<Dept> children;
 *     // getters / setters ...
 * }
 * }</pre>
 *
 * @param <T> 实现类自身类型（自引用泛型）
 * @author leyland
 * @since 2.0.0
 */
public interface TreeNode<T extends TreeNode<T>> {

    /** 节点唯一标识. */
    Object getId();

    /** 父节点标识，{@code null} 表示根节点. */
    Object getParentId();

    /** 子节点列表. */
    List<T> getChildren();

    /** 设置子节点列表（由 {@link TreeBuilder} 回填）. */
    void setChildren(List<T> children);

    /** 是否为根节点. */
    default boolean isRoot() {
        return getParentId() == null;
    }

    /** 是否为叶子节点. */
    default boolean isLeaf() {
        return getChildren() == null || getChildren().isEmpty();
    }

    /** 子节点数量. */
    default int childCount() {
        return getChildren() == null ? 0 : getChildren().size();
    }
}
