package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * 不可变表达式抽象语法树节点。
 *
 * <p>源码范围使用零基左闭右开区间，子节点按源码顺序稳定排列。</p>
 */
public sealed interface AstNode permits LiteralNode, PathNode, FunctionCallNode,
        UnaryOperationNode, BinaryOperationNode, BetweenNode, ListLiteralNode {

    /**
     * 节点覆盖范围的 UTF-16 起始偏移。
     *
     * @return 包含的起始位置
     */
    int startPosition();

    /**
     * 节点覆盖范围的 UTF-16 结束偏移。
     *
     * @return 不包含的结束位置
     */
    int endPosition();

    /**
     * 用于统一迭代遍历的直接子节点视图。
     *
     * @return 按源码顺序排列的子节点
     */
    List<AstNode> children();
}

/**
 * AST 构造不变量的包内校验工具。
 */
final class AstNodes {

    /** 公开 AST 构造不能突破的内部深度硬边界。 */
    private static final int MAX_DEPTH = 256;

    /** 工具类不允许实例化。 */
    private AstNodes() {
    }

    /** 校验节点范围。 */
    static void requireRange(int startPosition, int endPosition) {
        if (startPosition < 0 || endPosition <= startPosition) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /** 校验单个子节点被父范围包含。 */
    static AstNode requireChild(
            AstNode child, int startPosition, int endPosition) {
        requireRange(startPosition, endPosition);
        if (child == null || child.startPosition() < startPosition
                || child.endPosition() > endPosition) {
            throw RuleEngineException.invalidArgument();
        }
        requireDepth(List.of(child));
        return child;
    }

    /** 防御复制并校验子节点顺序与父范围。 */
    static List<AstNode> copyChildren(
            List<? extends AstNode> children,
            boolean requireNonEmpty,
            int startPosition,
            int endPosition) {
        requireRange(startPosition, endPosition);
        try {
            List<AstNode> snapshot = List.copyOf(children);
            if (requireNonEmpty && snapshot.isEmpty()) {
                throw RuleEngineException.invalidArgument();
            }
            int previousEnd = startPosition;
            for (AstNode child : snapshot) {
                if (child.startPosition() < previousEnd
                        || child.endPosition() > endPosition) {
                    throw RuleEngineException.invalidArgument();
                }
                previousEnd = child.endPosition();
            }
            requireDepth(snapshot);
            return snapshot;
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /**
     * 以即将创建的父节点为第一层，迭代检查全部子树深度。
     *
     * <p>使用身份映射记录同一节点已访问的最大深度，共享 DAG 只有在更深路径出现时
     * 才重新展开；全程不调用递归相等或哈希方法。</p>
     *
     * @param children 即将挂到新父节点下的直接子节点
     */
    static void requireDepth(List<? extends AstNode> children) {
        Deque<DepthEntry> pending = new ArrayDeque<>();
        for (AstNode child : children) {
            pending.addLast(new DepthEntry(child, 2));
        }
        IdentityHashMap<AstNode, Integer> greatestDepth = new IdentityHashMap<>();
        while (!pending.isEmpty()) {
            DepthEntry entry = pending.removeLast();
            if (entry.depth > MAX_DEPTH) {
                throw RuleEngineException.invalidArgument();
            }
            Integer previous = greatestDepth.get(entry.node);
            if (previous != null && previous >= entry.depth) {
                continue;
            }
            greatestDepth.put(entry.node, entry.depth);
            for (AstNode child : entry.node.children()) {
                pending.addLast(new DepthEntry(child, entry.depth + 1));
            }
        }
    }

    /** 显式栈中的节点和相对深度。 */
    private static final class DepthEntry {

        /** 待检查节点。 */
        private final AstNode node;

        /** 以即将创建的父节点为第一层计算的深度。 */
        private final int depth;

        /** 创建显式深度遍历项。 */
        private DepthEntry(AstNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
