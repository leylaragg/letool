package com.github.leyland.letool.data.letool.tool.util.node;

import java.util.ArrayList;
import java.util.List;

// 基础树节点实现
public class BaseTreeNode implements TreeNode<Long> {

    private Long id;

    private Long parentId;

    private String name;

    private List<TreeNode<Long>> children = new ArrayList<>();

    public BaseTreeNode(Long id, Long parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    // getter和setter方法

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<TreeNode<Long>> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<TreeNode<Long>> children) {
        this.children = children;
    }
}
