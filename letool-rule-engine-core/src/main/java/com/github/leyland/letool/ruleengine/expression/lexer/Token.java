package com.github.leyland.letool.ruleengine.expression.lexer;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.Objects;

/**
 * 保留源码范围和规范值的不可变词法单元。
 */
public final class Token {

    /** 词法分类。 */
    private final TokenType type;

    /** 与源码切片完全一致的原始文本。 */
    private final String rawText;

    /** 后续阶段使用的安全规范文本。 */
    private final String normalizedValue;

    /** UTF-16 起始偏移，包含该位置。 */
    private final int startPosition;

    /** UTF-16 结束偏移，不包含该位置。 */
    private final int endPosition;

    /**
     * 创建词法单元。
     *
     * @param type 词法单元类型
     * @param rawText 源码原始文本
     * @param normalizedValue 仅由安全字符串表示的规范值
     * @param startPosition 零基起始位置，包含
     * @param endPosition 零基结束位置，不包含
     */
    Token(
            TokenType type,
            String rawText,
            String normalizedValue,
            int startPosition,
            int endPosition) {
        if (type == null || rawText == null || normalizedValue == null
                || startPosition < 0 || endPosition < startPosition
                || rawText.length() != endPosition - startPosition
                || type != TokenType.EOF && rawText.isEmpty()
                || type == TokenType.EOF
                && (!rawText.isEmpty() || !normalizedValue.isEmpty()
                || startPosition != endPosition)) {
            throw RuleEngineException.invalidArgument();
        }
        this.type = type;
        this.rawText = rawText;
        this.normalizedValue = normalizedValue;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /**
     * 后续语法分析使用的词法分类。
     *
     * @return 词法单元类型
     */
    public TokenType type() {
        return type;
    }

    /**
     * 与源码范围对应的原始文本。
     *
     * @return 原始文本
     */
    public String rawText() {
        return rawText;
    }

    /**
     * 不依赖原始转义形式的规范值。
     *
     * @return 安全字符串规范值
     */
    public String normalizedValue() {
        return normalizedValue;
    }

    /**
     * 零基 UTF-16 起始偏移。
     *
     * @return 包含的起始位置
     */
    public int startPosition() {
        return startPosition;
    }

    /**
     * 零基 UTF-16 结束偏移。
     *
     * @return 不包含的结束位置
     */
    public int endPosition() {
        return endPosition;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Token that)) return false;
        return startPosition == that.startPosition
                && endPosition == that.endPosition
                && type == that.type
                && rawText.equals(that.rawText)
                && normalizedValue.equals(that.normalizedValue);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(type, rawText, normalizedValue, startPosition, endPosition);
    }
}
