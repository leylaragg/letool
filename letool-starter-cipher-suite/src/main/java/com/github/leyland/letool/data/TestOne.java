package com.github.leyland.letool.data;

import java.util.List;

/**
 * @ClassName <h2>TestOne</h2>
 * @Description
 * @Author rungo
 * @Date 7/23/2025
 * @Version 1.0
 **/
public class TestOne {

    public class Tree {

        public List<Node> node;

        class Node {
            String val;

        }

    }


    public static void main(String[] args) {

    }

    public Tree reTree(Tree tree) {
        if (tree != null) {



            if (tree.node != null) {
                return reTree(tree);
            }
            return tree;
        }
        return null;
    }
}
