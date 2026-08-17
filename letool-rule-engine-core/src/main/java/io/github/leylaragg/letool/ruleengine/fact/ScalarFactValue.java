package io.github.leylaragg.letool.ruleengine.fact;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 字符串、布尔、数值和时间等不可变标量事实值。
 */
public final class ScalarFactValue implements FactValue {

    /** 标量在规则类型系统中的分类。 */
    private final FactKind kind;

    /** 与分类精确匹配的 JDK 不可变值。 */
    private final Object value;

    /**
     * 创建标量事实值。
     *
     * @param kind 标量类型
     * @param value 与类型匹配的非空不可变值
     */
    ScalarFactValue(FactKind kind, Object value) {
        if (!isCompatible(kind, value)) {
            throw RuleEngineException.invalidArgument();
        }
        this.kind = kind;
        this.value = value;
    }

    /** 只接受固定 JDK 类型，避免子类携带可变或自定义行为。 */
    private static boolean isCompatible(FactKind kind, Object value) {
        if (kind == null || value == null) return false;
        return switch (kind) {
            case STRING -> value.getClass() == String.class;
            case BOOLEAN -> value.getClass() == Boolean.class;
            case INTEGER -> value.getClass() == BigInteger.class;
            case DECIMAL -> value.getClass() == java.math.BigDecimal.class;
            case DATE -> value.getClass() == java.time.LocalDate.class;
            case DATE_TIME -> value.getClass() == java.time.LocalDateTime.class;
            case INSTANT -> value.getClass() == java.time.Instant.class;
            default -> false;
        };
    }

    @Override
    public FactKind kind() {
        return kind;
    }

    @Override
    public Object toSafeJavaValue() {
        return value;
    }

    @Override
    public BigInteger asBigInteger() {
        if (kind != FactKind.INTEGER) {
            throw RuleEngineException.invalidArgument();
        }
        return (BigInteger) value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScalarFactValue that)) {
            return false;
        }
        return kind == that.kind && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
