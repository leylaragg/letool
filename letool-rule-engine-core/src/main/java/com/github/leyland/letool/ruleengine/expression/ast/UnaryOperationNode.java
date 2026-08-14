package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.expression.lexer.TokenType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变一元操作节点。
 */
public final class UnaryOperationNode implements AstNode {

    /** 语法树允许保存的一元运算符集合。 */
    private static final Set<TokenType> OPERATORS = Set.of(
            TokenType.NOT, TokenType.PLUS, TokenType.MINUS,
            TokenType.IS_NULL, TokenType.IS_NOT_NULL);

    /** 规范化的一元运算符。 */
    private final TokenType operator;

    /** 唯一操作数。 */
    private final AstNode operand;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建一元操作节点。
     *
     * @param operator 一元运算符
     * @param operand 操作数
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public UnaryOperationNode(
            TokenType operator, AstNode operand,
            int startPosition, int endPosition) {
        if (operator == null || !OPERATORS.contains(operator)) {
            throw RuleEngineException.invalidArgument();
        }
        this.operand = AstNodes.requireChild(operand, startPosition, endPosition);
        this.operator = operator;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 一元运算符 */
    public TokenType operator() {
        return operator;
    }

    /** @return 操作数 */
    public AstNode operand() {
        return operand;
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
        return List.of(operand);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UnaryOperationNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && operator == that.operator && operand.equals(that.operand);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(operator, operand, startPosition, endPosition);
    }
}
