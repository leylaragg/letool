package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.api.EvaluationTrace;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactKind;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;

import java.util.List;

/**
 * 表达式成功值或带稳定诊断和框架异常的不可变求值结果。
 */
public final class ExpressionEvaluationResult {

    /** 成功时存在的不可变事实值。 */
    private final FactValue value;

    /** 失败时存在的结构化运行期诊断。 */
    private final List<RuleDiagnostic> diagnostics;

    /** 开启或关闭状态明确的求值轨迹快照。 */
    private final EvaluationTrace trace;

    /** 可选技术原因链，其消息不会进入诊断展示。 */
    private final RuleEngineException failureCause;

    private ExpressionEvaluationResult(FactValue value, List<RuleDiagnostic> diagnostics,
            EvaluationTrace trace, RuleEngineException failureCause) {
        this.value = value;
        this.diagnostics = diagnostics;
        this.trace = trace;
        this.failureCause = failureCause;
    }

    /**
     * 创建不携带诊断的成功结果。
     *
     * @param value 非空求值结果
     * @param trace 安全轨迹
     * @return 成功结果
     * @throws RuleEngineException 值或轨迹为空时抛出
     */
    public static ExpressionEvaluationResult success(FactValue value, EvaluationTrace trace) {
        if (value == null || trace == null) throw RuleEngineException.invalidArgument();
        return new ExpressionEvaluationResult(value, List.of(), trace, null);
    }

    /**
     * 创建至少携带一条错误诊断的失败结果。
     *
     * @param diagnostics 至少包含一条错误的不可变诊断来源
     * @param trace 安全轨迹
     * @param failureCause 非空稳定框架异常
     * @return 失败结果
     * @throws RuleEngineException 参数为空、诊断不属于运行期错误或不满足失败不变量时抛出
     */
    public static ExpressionEvaluationResult failure(List<RuleDiagnostic> diagnostics,
            EvaluationTrace trace, RuleEngineException failureCause) {
        if (diagnostics == null || trace == null || failureCause == null) {
            throw RuleEngineException.invalidArgument();
        }
        List<RuleDiagnostic> snapshot;
        try {
            snapshot = List.copyOf(diagnostics);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
        if (snapshot.isEmpty() || snapshot.stream().anyMatch(value ->
                value.severity() != DiagnosticSeverity.ERROR
                        || value.phase() != io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase.RUNTIME)) {
            throw RuleEngineException.invalidArgument();
        }
        return new ExpressionEvaluationResult(null, snapshot, trace, failureCause);
    }

    /** @return 求值成功时返回 {@code true} */
    public boolean isSuccessful() { return value != null; }

    /** @return 不可变运行期诊断 */
    public List<RuleDiagnostic> diagnostics() { return diagnostics; }

    /** @return 不可变安全轨迹 */
    public EvaluationTrace trace() { return trace; }

    /**
     * 要求成功并读取事实值。
     *
     * @return 求值事实值
     * @throws RuleEngineException 结果失败时抛出保存的同一框架异常
     */
    public FactValue requireValue() {
        if (value == null) throw failureCause;
        return value;
    }

    /**
     * 要求成功布尔结果。
     *
     * @return 布尔值
     * @throws RuleEngineException 结果失败或成功值不是布尔类型时抛出
     */
    public boolean requireBoolean() {
        FactValue required = requireValue();
        if (required.kind() != FactKind.BOOLEAN) throw RuleEngineException.invalidArgument();
        return (Boolean) required.toSafeJavaValue();
    }

    /**
     * 失败结果保留的可选技术原因链。
     *
     * @return 失败异常；成功时为 {@code null}
     */
    public RuleEngineException failureCause() { return failureCause; }
}
