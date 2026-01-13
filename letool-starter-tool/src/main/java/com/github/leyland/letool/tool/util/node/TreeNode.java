package com.github.leyland.letool.tool.util.node;

import java.util.List;

public interface TreeNode<T> {

    /**
     * 节点ID
     */
    T getId();

    /**
     * 父节点ID
     */
    T getParentId();

    /**
     * 节点名称
     */
    String getName();

    /**
     * 设置子节点
     */
    void setChildren(List<TreeNode<T>> children);

    /**
     * 获取子节点
     */
    List<TreeNode<T>> getChildren();


}
