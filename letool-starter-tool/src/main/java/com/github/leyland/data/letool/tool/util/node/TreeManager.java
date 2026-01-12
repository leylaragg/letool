package com.github.leyland.data.letool.tool.util.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeManager<T> {

    private List<TreeNode<T>> nodeList;

    public TreeManager(List<TreeNode<T>> nodeList) {
        this.nodeList = nodeList;
    }

    // C - 创建节点
    public void addNode(TreeNode<T> newNode) {
        nodeList.add(newNode);
    }

    // R - 根据ID查找节点
    public TreeNode<T> findNodeById(T id) {
        return nodeList.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // R - 构建完整树结构
    public List<TreeNode<T>> buildTree() {
        Map<T, TreeNode<T>> nodeMap = new HashMap<>();
        List<TreeNode<T>> rootNodes = new ArrayList<>();

        // 所有节点存入map
        for (TreeNode<T> node : nodeList) {
            nodeMap.put(node.getId(), node);
        }

        // 构建父子关系
        for (TreeNode<T> node : nodeList) {
            T parentId = node.getParentId();
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                rootNodes.add(node); // 根节点
            } else {
                TreeNode<T> parent = nodeMap.get(parentId);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return rootNodes;
    }

    // R - 查找子节点（递归）
    public List<TreeNode<T>> findChildren(T parentId) {
        List<TreeNode<T>> result = new ArrayList<>();
        TreeNode<T> parent = findNodeById(parentId);
        if (parent != null && parent.getChildren() != null) {
            result.addAll(parent.getChildren());
            for (TreeNode<T> child : parent.getChildren()) {
                result.addAll(findChildren(child.getId()));
            }
        }
        return result;
    }

    // U - 更新节点
    public boolean updateNode(T id, String newName) {
        TreeNode<T> node = findNodeById(id);
        if (node != null) {
            // 这里需要具体实现类的setter方法
            if (node instanceof BaseTreeNode) {
                ((BaseTreeNode) node).setName(newName);
                return true;
            }
        }
        return false;
    }

    // D - 删除节点及其子树
    public boolean deleteNode(T id) {
        TreeNode<T> nodeToDelete = findNodeById(id);
        if (nodeToDelete == null) return false;

        // 递归删除所有子节点
        List<TreeNode<T>> allChildren = findChildren(id);
        for (TreeNode<T> child : allChildren) {
            nodeList.remove(child);
        }

        // 删除当前节点
        return nodeList.remove(nodeToDelete);
    }

    // 获取平铺列表
    public List<TreeNode<T>> getNodeList() {
        return new ArrayList<>(nodeList);
    }
}

