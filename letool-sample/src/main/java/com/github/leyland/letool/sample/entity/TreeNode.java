package com.github.leyland.letool.sample.entity;

import java.util.List;

/**
 * 部门实体 —— 实现 letool 泛型树节点接口，演示树构建.
 */
public class TreeNode implements com.github.leyland.letool.datastructure.tree.TreeNode<TreeNode> {

    private Long id;
    private Long parentId;
    private String name;
    private List<TreeNode> children;

    public TreeNode() {}

    public TreeNode(Long id, Long parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @Override
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public List<TreeNode> getChildren() { return children; }

    @Override
    public void setChildren(List<TreeNode> children) { this.children = children; }
}
