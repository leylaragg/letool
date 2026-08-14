package com.github.leyland.letool.ruleengine.diagnostic;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 不依赖展示区域设置的不可变结构化诊断。
 */
public final class RuleDiagnostic {

    /** 单条诊断允许携带的安全参数数量上限。 */
    private static final int MAX_ARGUMENTS = 16;

    /** 单个字符串诊断参数允许的最大字符数。 */
    public static final int MAX_ARGUMENT_STRING_LENGTH = 256;

    /** 建议表达式允许保留的最大字符数。 */
    private static final int MAX_SUGGESTION_LENGTH = 512;

    /** 整数及小数未缩放值允许的最大位数。 */
    private static final int MAX_NUMERIC_BIT_LENGTH = 768;

    /** 小数绝对缩放范围上限。 */
    private static final int MAX_DECIMAL_SCALE = 240;

    /** 稳定诊断码。 */
    private final RuleDiagnosticCode code;

    /** 诊断严重级别。 */
    private final DiagnosticSeverity severity;

    /** 诊断产生阶段。 */
    private final DiagnosticPhase phase;

    /** UTF-16 起始偏移，包含该位置。 */
    private final int startPosition;

    /** UTF-16 结束偏移，不包含该位置。 */
    private final int endPosition;

    /** 已验证类型并冻结的安全参数。 */
    private final List<Object> arguments;

    /** 可选的修正表达式建议。 */
    private final String suggestedExpression;

    /**
     * 创建结构化诊断。
     *
     * @param code 稳定诊断码
     * @param severity 严重级别
     * @param phase 产生阶段
     * @param startPosition 零基起始位置，包含
     * @param endPosition 零基结束位置，不包含
     * @param arguments 可稳定渲染的安全参数，最多十六项
     * @param suggestedExpression 可选建议表达式，最多五百一十二个字符
     * @throws RuleEngineException 参数不符合安全契约时抛出
     */
    public RuleDiagnostic(
            RuleDiagnosticCode code,
            DiagnosticSeverity severity,
            DiagnosticPhase phase,
            int startPosition,
            int endPosition,
            List<Object> arguments,
            String suggestedExpression) {
        if (code == null || severity == null || phase == null
                || startPosition < 0 || endPosition < startPosition
                || suggestedExpression != null
                && suggestedExpression.length() > MAX_SUGGESTION_LENGTH) {
            throw RuleEngineException.invalidArgument();
        }
        this.code = code;
        this.severity = severity;
        this.phase = phase;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.arguments = copyArguments(arguments);
        this.suggestedExpression = suggestedExpression;
    }

    /**
     * 稳定的机器可读诊断标识。
     *
     * @return 稳定诊断码
     */
    public RuleDiagnosticCode code() {
        return code;
    }

    /**
     * 调用方决定阻断还是提示所需的严重级别。
     *
     * @return 严重级别
     */
    public DiagnosticSeverity severity() {
        return severity;
    }

    /**
     * 诊断来自词法、语法、语义或运行期中的哪个阶段。
     *
     * @return 诊断阶段
     */
    public DiagnosticPhase phase() {
        return phase;
    }

    /**
     * 源文本中的零基 UTF-16 起始偏移。
     *
     * @return 包含的起始位置
     */
    public int startPosition() {
        return startPosition;
    }

    /**
     * 源文本中的零基 UTF-16 结束偏移。
     *
     * @return 不包含的结束位置
     */
    public int endPosition() {
        return endPosition;
    }

    /**
     * 可由诊断消息格式化器稳定渲染的不可变参数。
     *
     * @return 安全参数副本
     */
    public List<Object> arguments() {
        return arguments;
    }

    /**
     * 面向编辑器的可选修正表达式。
     *
     * @return 建议表达式；没有建议时为 {@code null}
     */
    public String suggestedExpression() {
        return suggestedExpression;
    }

    /**
     * 防御性复制并验证诊断参数。
     *
     * @param source 不可信参数集合
     * @return 不可变安全参数
     */
    private static List<Object> copyArguments(List<Object> source) {
        if (source == null) {
            throw RuleEngineException.invalidArgument();
        }
        try {
            List<Object> copied = new ArrayList<>(Math.min(source.size(), MAX_ARGUMENTS));
            int visited = 0;
            for (Object argument : source) {
                if (++visited > MAX_ARGUMENTS || !isSafeArgument(argument)) {
                    throw RuleEngineException.invalidArgument();
                }
                copied.add(argument instanceof Enum<?> enumeration
                        ? enumeration.name() : argument);
            }
            return Collections.unmodifiableList(copied);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /**
     * 判断参数是否可以不调用任意对象方法地稳定渲染。
     *
     * @param argument 待校验参数
     * @return 安全时返回 {@code true}
     */
    private static boolean isSafeArgument(Object argument) {
        if (argument instanceof String text) {
            return text.length() <= MAX_ARGUMENT_STRING_LENGTH;
        }
        if (argument == null) {
            return false;
        }
        Class<?> type = argument.getClass();
        return type == Boolean.class
                || type == BigInteger.class
                && ((BigInteger) argument).bitLength() <= MAX_NUMERIC_BIT_LENGTH
                || type == BigDecimal.class
                && isSafeDecimal((BigDecimal) argument)
                || type == LocalDate.class
                || type == LocalDateTime.class
                || type == Instant.class
                || argument instanceof Enum<?> enumeration
                && enumeration.name().length() <= MAX_ARGUMENT_STRING_LENGTH;
    }

    /**
     * 判断小数的未缩放值和 scale 是否能产生有界展示文本。
     *
     * @param decimal 待校验小数
     * @return 在固定边界内时返回 {@code true}
     */
    private static boolean isSafeDecimal(BigDecimal decimal) {
        return decimal.unscaledValue().bitLength() <= MAX_NUMERIC_BIT_LENGTH
                && decimal.scale() >= -MAX_DECIMAL_SCALE
                && decimal.scale() <= MAX_DECIMAL_SCALE;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuleDiagnostic that)) return false;
        return startPosition == that.startPosition
                && endPosition == that.endPosition
                && code == that.code
                && severity == that.severity
                && phase == that.phase
                && arguments.equals(that.arguments)
                && Objects.equals(suggestedExpression, that.suggestedExpression);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(code, severity, phase, startPosition, endPosition,
                arguments, suggestedExpression);
    }
}
