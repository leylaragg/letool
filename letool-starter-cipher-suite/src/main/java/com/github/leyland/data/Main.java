package com.github.leyland.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static class Table {
        int id;
        String name;
        int parentId;

        public int getId() {
            return id;
        }

        public Table setId(int id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Table setName(String name) {
            this.name = name;
            return this;
        }

        public int getParentId() {
            return parentId;
        }

        public Table setParentId(int parentId) {
            this.parentId = parentId;
            return this;
        }

        public Table(int id, String name, int parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }
    }

    public static class TableDTO {
        Table parent;
        List<TableDTO> child;

        public Table getParent() {
            return parent;
        }

        public TableDTO setParent(Table parent) {
            this.parent = parent;
            return this;
        }

        public List<TableDTO> getChild() {
            return child;
        }

        public TableDTO setChild(List<TableDTO> child) {
            this.child = child;
            return this;
        }
    }

    public static void main(String[] args) {

        List<Table> tableList = new ArrayList<>();
        tableList.add(new Table(1, "test1", 0));
        tableList.add(new Table(2, "test2", 1));
        tableList.add(new Table(3, "test3", 0));
        tableList.add(new Table(4, "test4", 2));
        tableList.add(new Table(5, "test5", 2));
        tableList.add(new Table(6, "test6", 1));


        // 1. 创建节点ID到DTO的映射
        Map<Integer, TableDTO> nodeMap = new HashMap<>();

        // 2. 创建所有节点的DTO对象
        for (Table node : tableList ) {
            TableDTO dto = new TableDTO();
            dto.setParent(node);
            dto.setChild(new ArrayList<>());
            nodeMap.put(node.id, dto);
        }

        // 3. 创建结果集（根节点列表）
        List<TableDTO> roots = new ArrayList<>();

        // 4. 建立节点关系
        for (Table node : tableList ) {
            int parentId = node.parentId;
            TableDTO currentDTO = nodeMap.get(node.id);

            if (parentId == 0) {
                // 根节点：直接添加到结果集
                roots.add(currentDTO);
            } else {
                // 非根节点：查找父节点并添加为子节点
                TableDTO parentDTO = nodeMap.get(parentId);
                if (parentDTO != null) {
                    parentDTO.child.add(currentDTO);
                }
            }
        }

        printTree(roots, 0);



    }

    private static void printTree(List<TableDTO> tree, int level) {
        for (TableDTO dto : tree) {
            Table node = dto.getParent();
            System.out.println("  ".repeat(level * 2) + "├─ " + node.name + " (id=" + node.id + ")");

            // 递归打印子节点
            if (!dto.getChild().isEmpty()) {
                printTree(dto.getChild(), level + 1);
            }
        }
    }







}