package com.github.leyland.letool.data.letool.tool.util.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeUtil {

    // 列表转树形结构
    public static <T> List<TreeNode<T>> listToTree(List<TreeNode<T>> list) {
        Map<T, TreeNode<T>> nodeMap = new HashMap<>();
        List<TreeNode<T>> result = new ArrayList<>();

        for (TreeNode<T> node : list) {
            nodeMap.put(node.getId(), node);
        }

        for (TreeNode<T> node : list) {
            T parentId = node.getParentId();
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                result.add(node);
            } else {
                TreeNode<T> parent = nodeMap.get(parentId);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return result;
    }

    // 树形结构转列表（平铺）
    public static <T> List<TreeNode<T>> treeToList(List<TreeNode<T>> tree) {
        List<TreeNode<T>> result = new ArrayList<>();
        flattenTree(tree, result);
        return result;
    }

    private static <T> void flattenTree(List<TreeNode<T>> nodes, List<TreeNode<T>> result) {
        for (TreeNode<T> node : nodes) {
            result.add(node);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flattenTree(node.getChildren(), result);
            }
        }
    }


    public static class TreeExample {
        public static void main(String[] args) {
            // 创建节点列表
            List<TreeNode<Long>> nodes = new ArrayList<>();
            nodes.add(new BaseTreeNode(1L, null, "根节点"));
            nodes.add(new BaseTreeNode(2L, 1L, "子节点1"));
            nodes.add(new BaseTreeNode(3L, 1L, "子节点2"));
            nodes.add(new BaseTreeNode(4L, 2L, "孙子节点1"));

            // 创建管理器
            TreeManager<Long> treeManager = new TreeManager<>(nodes);

            // 构建树
            List<TreeNode<Long>> tree = treeManager.buildTree();
            System.out.println("完整树结构: ");
            tree.forEach(intactNode -> {
                if (intactNode.getChildren() != null) {
                    System.out.println("节点: " + intactNode.getName());
                    intactNode.getChildren().forEach(child -> System.out.println("子节点: " + child.getName()));
                }
            });

            // 查询操作
            TreeNode<Long> node = treeManager.findNodeById(2L);
            System.out.println("查找节点2: " + node.getName());

            // 添加新节点
            treeManager.addNode(new BaseTreeNode(5L, 2L, "新节点"));

            // 更新节点
            treeManager.updateNode(3L, "更新后的节点");

            // 删除节点
            treeManager.deleteNode(4L);

            // 重新构建树查看结果
            List<TreeNode<Long>> updatedTree = treeManager.buildTree();
            System.out.println("更新后的树结构: ");
            updatedTree.forEach(updateNode -> System.out.println("节点: " + updateNode.getName()));
        }
    }
}
