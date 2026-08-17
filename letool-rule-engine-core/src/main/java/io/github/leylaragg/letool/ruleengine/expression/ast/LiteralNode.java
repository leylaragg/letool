package io.github.leylaragg.letool.ruleengine.expression.ast;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 保存字面量分类和安全规范文本的不可变节点。
 */
public final class LiteralNode implements AstNode {

    /** 允许作为字面量节点保存的 Token 类型。 */
    private static final Set<TokenType> TYPES = Set.of(
            TokenType.STRING, TokenType.BOOLEAN, TokenType.INTEGER, TokenType.DECIMAL,
            TokenType.NULL, TokenType.DATE, TokenType.DATETIME, TokenType.INSTANT);

    /** 字面量分类。 */
    private final TokenType literalType;

    /** Lexer 生成的规范文本，不保留原始转义形式。 */
    private final String normalizedValue;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建字面量节点。
     *
     * @param literalType 受支持的字面量 Token 类型
     * @param normalizedValue Lexer 生成的安全规范文本
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public LiteralNode(
            TokenType literalType, String normalizedValue,
            int startPosition, int endPosition) {
        AstNodes.requireRange(startPosition, endPosition);
        if (literalType == null || normalizedValue == null || !TYPES.contains(literalType)) {
            throw RuleEngineException.invalidArgument();
        }
        this.literalType = literalType;
        this.normalizedValue = normalizedValue;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 字面量 Token 类型 */
    public TokenType literalType() {
        return literalType;
    }

    /** @return 安全规范文本 */
    public String normalizedValue() {
        return normalizedValue;
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
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LiteralNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && literalType == that.literalType
                && normalizedValue.equals(that.normalizedValue);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(literalType, normalizedValue, startPosition, endPosition);
    }
}
