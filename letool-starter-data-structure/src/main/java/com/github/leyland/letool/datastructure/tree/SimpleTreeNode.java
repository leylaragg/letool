package com.github.leyland.letool.datastructure.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用于包装普通业务对象的可变简单树节点。
 *
 * <p>业务实体不方便实现 {@link TreeNode} 时，可通过
 * {@link TreeBuilder#buildSimple(List, java.util.function.Function, java.util.function.Function)}
 * 自动创建此包装节点。节点按 ID 进行值相等比较，因此作为哈希容器键使用后不应修改 ID。</p>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class SimpleTreeNode<T> implements TreeNode<SimpleTreeNode<T>> {

    /** 节点 ID。 */
    private Object id;

    /** 父节点 ID。 */
    private Object parentId;

    /** 业务数据负载。 */
    private T data;

    /** 子节点列表。 */
    private List<SimpleTreeNode<T>> children;

    /**
     * 创建字段为空、子节点列表已初始化的节点。
     */
    public SimpleTreeNode() {
        this.children = new ArrayList<>();
    }

    /**
     * 创建完整简单树节点。
     *
     * @param id 节点 ID；参与构建时不允许为空
     * @param parentId 父节点 ID；根节点传入空值
     * @param data 业务数据负载；允许为空
     */
    public SimpleTreeNode(Object id, Object parentId, T data) {
        this.id = id;
        this.parentId = parentId;
        this.data = data;
        this.children = new ArrayList<>();
    }

    /**
     * 创建完整简单树节点。
     *
     * @param id 节点 ID；参与构建时不允许为空
     * @param parentId 父节点 ID；根节点传入空值
     * @param data 业务数据负载；允许为空
     * @param <T> 节点数据类型
     * @return 新节点
     */
    public static <T> SimpleTreeNode<T> of(Object id, Object parentId, T data) {
        return new SimpleTreeNode<>(id, parentId, data);
    }

    /**
     * 添加一个直接子节点。
     *
     * @param child 子节点；调用方应保证非空且不会形成环
     * @return 当前节点
     */
    public SimpleTreeNode<T> addChild(SimpleTreeNode<T> child) {
        this.children.add(child);
        return this;
    }

    /**
     * 按参数顺序添加多个直接子节点。
     *
     * @param nodes 子节点数组；调用方应保证数组和元素非空且不会形成环
     * @return 当前节点
     */
    @SafeVarargs
    public final SimpleTreeNode<T> addChildren(SimpleTreeNode<T>... nodes) {
        for (SimpleTreeNode<T> node : nodes) {
            this.children.add(node);
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public Object getId() {
        return id;
    }

    /**
     * 修改节点 ID。
     *
     * @param id 新节点 ID；参与构建时不允许为空
     */
    public void setId(Object id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    public Object getParentId() {
        return parentId;
    }

    /**
     * 修改父节点 ID。
     *
     * @param parentId 新父节点 ID；空值表示根节点
     */
    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    /**
     * 获取业务数据负载。
     *
     * @return 业务数据；可能为空
     */
    public T getData() {
        return data;
    }

    /**
     * 修改业务数据负载。
     *
     * @param data 新业务数据；允许为空
     */
    public void setData(T data) {
        this.data = data;
    }

    /** {@inheritDoc} */
    @Override
    public List<SimpleTreeNode<T>> getChildren() {
        return children;
    }

    /** {@inheritDoc} */
    @Override
    public void setChildren(List<SimpleTreeNode<T>> children) {
        this.children = children;
    }

    /**
     * 按节点 ID 判断值相等。
     *
     * @param object 待比较对象
     * @return 节点 ID 相等时为 {@code true}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SimpleTreeNode<?> that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    /**
     * 按节点 ID 计算哈希值。
     *
     * @return 节点 ID 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 返回不递归展开子节点的诊断文本。
     *
     * @return 节点诊断文本
     */
    @Override
    public String toString() {
        return "SimpleTreeNode{id=" + id + ", parentId=" + parentId + ", data=" + data + "}";
    }
}
