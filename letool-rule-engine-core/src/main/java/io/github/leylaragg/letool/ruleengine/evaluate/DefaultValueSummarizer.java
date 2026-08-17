package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.ArrayFactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.ObjectFactValue;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 不调用任意宿主对象方法且不深度遍历容器的默认值摘要器。
 */
public final class DefaultValueSummarizer implements ValueSummarizer {

    /** {@inheritDoc} */
    @Override
    public String summarize(FactValue value, int maximumLength) {
        if (value == null || maximumLength <= 0) {
            throw RuleEngineException.invalidArgument();
        }
        String summary = switch (value.kind()) {
            case NULL -> "NULL";
            case STRING -> summarizeString((String) value.toSafeJavaValue(), maximumLength);
            case BOOLEAN -> "BOOLEAN(" + value.toSafeJavaValue() + ")";
            case INTEGER -> summarizeInteger((BigInteger) value.toSafeJavaValue());
            case DECIMAL -> summarizeDecimal((BigDecimal) value.toSafeJavaValue());
            case DATE -> "DATE(" + value.toSafeJavaValue() + ")";
            case DATE_TIME -> "DATE_TIME(" + value.toSafeJavaValue() + ")";
            case INSTANT -> "INSTANT(" + value.toSafeJavaValue() + ")";
            case ARRAY -> "ARRAY(size=" + ((ArrayFactValue) value).size() + ")";
            case OBJECT -> "OBJECT(size=" + ((ObjectFactValue) value).size() + ")";
        };
        return truncate(summary, maximumLength);
    }

    private static String summarizeString(String value, int maximumLength) {
        int prefixBudget = Math.max(0, maximumLength - 9);
        String prefix = value.substring(0, Math.min(value.length(), prefixBudget));
        return "STRING(" + prefix + (prefix.length() < value.length() ? "…" : "") + ")";
    }

    private static String summarizeInteger(BigInteger value) {
        if (value.bitLength() > 256) return "INTEGER(bits=" + value.bitLength() + ")";
        return "INTEGER(" + value + ")";
    }

    private static String summarizeDecimal(BigDecimal value) {
        if (value.precision() > 128 || Math.abs((long) value.scale()) > 256) {
            return "DECIMAL(precision=" + value.precision() + ",scale=" + value.scale() + ")";
        }
        return "DECIMAL(" + value.toPlainString() + ")";
    }

    private static String truncate(String value, int maximumLength) {
        if (value.length() <= maximumLength) return value;
        if (maximumLength == 1) return "…";
        return value.substring(0, maximumLength - 1) + "…";
    }
}
