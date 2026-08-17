package io.github.leylaragg.letool.datastructure.tree;

import java.util.List;

/**
 * 可由 {@link TreeBuilder} 构建、由 {@link TreeUtil} 操作的泛型树节点契约。
 *
 * <p>接口使用自引用泛型，业务实体实现后无需转换即可构建树。节点 ID 在同一次构建中必须非空且唯一；
 * 父节点 ID 为空表示根节点。构建器会通过 {@link #setChildren(List)} 回填新的可修改子节点列表。</p>
 *
 * @param <T> 实现类自身类型
 * @author leyland
 * @since 2.0.0
 */
public interface TreeNode<T extends TreeNode<T>> {

    /**
     * 获取节点唯一标识。
     *
     * @return 节点 ID；参与构建时不允许为空
     */
    Object getId();

    /**
     * 获取父节点标识。
     *
     * @return 父节点 ID；根节点返回 {@code null}
     */
    Object getParentId();

    /**
     * 获取子节点列表。
     *
     * @return 子节点列表；未初始化时允许为空
     */
    List<T> getChildren();

    /**
     * 回填子节点列表。
     *
     * @param children 子节点列表；{@link TreeBuilder} 传入非空、可修改的独立列表
     */
    void setChildren(List<T> children);

    /**
     * 判断当前节点是否为声明的根节点。
     *
     * <p>该方法只判断父节点 ID 是否为空，不检查父节点是否真实存在。</p>
     *
     * @return 父节点 ID 为空时为 {@code true}
     */
    default boolean isRoot() {
        return getParentId() == null;
    }

    /**
     * 判断当前节点是否没有子节点。
     *
     * @return 子节点列表为空引用或空列表时为 {@code true}
     */
    default boolean isLeaf() {
        return getChildren() == null || getChildren().isEmpty();
    }

    /**
     * 获取直接子节点数量。
     *
     * @return 子节点数量；子节点列表为空引用时返回零
     */
    default int childCount() {
        return getChildren() == null ? 0 : getChildren().size();
    }
}
