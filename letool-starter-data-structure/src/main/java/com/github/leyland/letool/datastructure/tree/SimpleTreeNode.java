package com.github.leyland.letool.datastructure.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 简单树节点 —— 适用于不方便让实体实现 {@link TreeNode} 接口的场景，将数据包装在此节点中再构建树.
 *
 * <pre>{@code
 * // 方式一：手动构造
 * SimpleTreeNode<Dept> node = SimpleTreeNode.<Dept>of(1L, null, dept)
 *     .addChild(SimpleTreeNode.of(2L, 1L, childDept));
 *
 * // 方式二：配合 TreeBuilder 从平列表构建
 * List<SimpleTreeNode<Dept>> tree = TreeBuilder.buildSimple(depts,
 *     Dept::getId, Dept::getParentId);
 * }</pre>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class SimpleTreeNode<T> implements TreeNode<SimpleTreeNode<T>> {

    private Object id;
    private Object parentId;
    private T data;
    private List<SimpleTreeNode<T>> children;

    public SimpleTreeNode() {
        this.children = new ArrayList<>();
    }

    public SimpleTreeNode(Object id, Object parentId, T data) {
        this.id = id;
        this.parentId = parentId;
        this.data = data;
        this.children = new ArrayList<>();
    }

    public static <T> SimpleTreeNode<T> of(Object id, Object parentId, T data) {
        return new SimpleTreeNode<>(id, parentId, data);
    }

    public SimpleTreeNode<T> addChild(SimpleTreeNode<T> child) {
        this.children.add(child);
        return this;
    }

    @SafeVarargs
    public final SimpleTreeNode<T> addChildren(SimpleTreeNode<T>... nodes) {
        for (SimpleTreeNode<T> node : nodes) {
            this.children.add(node);
        }
        return this;
    }

    // ---- getters / setters ----

    @Override
    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @Override
    public Object getParentId() {
        return parentId;
    }

    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public List<SimpleTreeNode<T>> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<SimpleTreeNode<T>> children) {
        this.children = children;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleTreeNode)) return false;
        SimpleTreeNode<?> that = (SimpleTreeNode<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimpleTreeNode{id=" + id + ", parentId=" + parentId + ", data=" + data + "}";
    }
}
