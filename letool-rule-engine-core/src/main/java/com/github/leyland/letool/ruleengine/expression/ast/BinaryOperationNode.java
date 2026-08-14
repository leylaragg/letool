package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.expression.lexer.TokenType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变二元操作节点。
 */
public final class BinaryOperationNode implements AstNode {

    /** 语法树允许保存的二元运算符集合。 */
    private static final Set<TokenType> OPERATORS = Set.of(
            TokenType.OR, TokenType.AND,
            TokenType.PLUS, TokenType.MINUS, TokenType.MULTIPLY,
            TokenType.DIVIDE, TokenType.MODULO,
            TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GE,
            TokenType.LT, TokenType.LE, TokenType.IN, TokenType.NOT_IN);

    /** 规范化的二元运算符。 */
    private final TokenType operator;

    /** 左操作数。 */
    private final AstNode left;

    /** 右操作数。 */
    private final AstNode right;

    /** 按源码顺序冻结的两项子节点视图。 */
    private final List<AstNode> children;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建二元操作节点。
     *
     * @param operator 二元运算符
     * @param left 左操作数
     * @param right 右操作数
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public BinaryOperationNode(
            TokenType operator, AstNode left, AstNode right,
            int startPosition, int endPosition) {
        if (operator == null || left == null || right == null
                || !OPERATORS.contains(operator)) {
            throw RuleEngineException.invalidArgument();
        }
        this.children = AstNodes.copyChildren(
                List.of(left, right), true, startPosition, endPosition);
        this.operator = operator;
        this.left = left;
        this.right = right;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 二元运算符 */
    public TokenType operator() {
        return operator;
    }

    /** @return 左操作数 */
    public AstNode left() {
        return left;
    }

    /** @return 右操作数 */
    public AstNode right() {
        return right;
    }

    /** {@inheritDoc} */
    @Override
    public int startPosition() {
        return startPosition;
    }

    /** {@inheritDoc} */
    @Override
    public int endPosition() {
        return endPosition;
    }

    /** {@inheritDoc} */
    @Override
    public List<AstNode> children() {
        return children;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BinaryOperationNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && operator == that.operator && left.equals(that.left) && right.equals(that.right);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(operator, left, right, startPosition, endPosition);
    }
}
