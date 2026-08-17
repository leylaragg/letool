package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * 单次表达式求值使用的不可变区域、时区、轨迹和资源限制选项。
 */
public final class EvaluationOptions {

    private static final EvaluationOptions DEFAULTS = new EvaluationOptions(
            Locale.ROOT, ZoneId.of("UTC"), false, EngineLimits.defaults());

    /** 文本和区域相关函数使用的区域设置。 */
    private final Locale locale;

    /** 时间转换函数使用的时区。 */
    private final ZoneId zoneId;

    /** 是否收集有界安全轨迹。 */
    private final boolean traceEnabled;

    /** 单次求值请求的资源上限，门面还会与引擎上限取更严格值。 */
    private final EngineLimits limits;

    private EvaluationOptions(
            Locale locale, ZoneId zoneId, boolean traceEnabled, EngineLimits limits) {
        if (locale == null || zoneId == null || limits == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.locale = locale;
        this.zoneId = zoneId;
        this.traceEnabled = traceEnabled;
        this.limits = limits;
    }

    /**
     * 共享的保守默认选项：根区域、UTC、关闭轨迹并采用默认限制。
     *
     * @return 可复用的默认选项
     */
    public static EvaluationOptions defaults() {
        return DEFAULTS;
    }

    /**
     * 创建求值选项。
     *
     * @param locale 函数调用区域
     * @param zoneId 函数调用时区
     * @param traceEnabled 是否启用安全轨迹
     * @param limits 求值资源限制
     * @return 不可变求值选项
     */
    public static EvaluationOptions of(
            Locale locale, ZoneId zoneId, boolean traceEnabled, EngineLimits limits) {
        return new EvaluationOptions(locale, zoneId, traceEnabled, limits);
    }

    /** @return 函数调用区域 */
    public Locale locale() { return locale; }

    /** @return 函数调用时区 */
    public ZoneId zoneId() { return zoneId; }

    /** @return 启用安全轨迹时返回 {@code true} */
    public boolean traceEnabled() { return traceEnabled; }

    /** @return 求值资源限制 */
    public EngineLimits limits() { return limits; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EvaluationOptions that
                && traceEnabled == that.traceEnabled
                && locale.equals(that.locale) && zoneId.equals(that.zoneId)
                && sameLimits(limits, that.limits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locale, zoneId, traceEnabled,
                limits.getMaxSourceLength(), limits.getMaxTokens(), limits.getMaxAstDepth(),
                limits.getMaxFunctionCalls(), limits.getMaxTraceNodes(),
                limits.getMaxSummaryLength(), limits.getMaxFactDepth(),
                limits.getMaxFactNodes(), limits.getMaxContainerSize());
    }

    private static boolean sameLimits(EngineLimits left, EngineLimits right) {
        return left.getMaxSourceLength() == right.getMaxSourceLength()
                && left.getMaxTokens() == right.getMaxTokens()
                && left.getMaxAstDepth() == right.getMaxAstDepth()
                && left.getMaxFunctionCalls() == right.getMaxFunctionCalls()
                && left.getMaxTraceNodes() == right.getMaxTraceNodes()
                && left.getMaxSummaryLength() == right.getMaxSummaryLength()
                && left.getMaxFactDepth() == right.getMaxFactDepth()
                && left.getMaxFactNodes() == right.getMaxFactNodes()
                && left.getMaxContainerSize() == right.getMaxContainerSize();
    }
}
