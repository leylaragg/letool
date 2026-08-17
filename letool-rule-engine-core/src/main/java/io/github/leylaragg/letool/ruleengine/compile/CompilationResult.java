package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 编译成功产物或失败诊断的不可变结果。
 *
 * @param <T> 编译产物类型
 */
public final class CompilationResult<T> {

    /** 单次编译结果允许携带的诊断数量上限。 */
    private static final int MAX_DIAGNOSTICS = 256;

    /** 成功时存在的编译产物。 */
    private final T compiled;

    /** 与调用方列表隔离的诊断快照。 */
    private final List<RuleDiagnostic> diagnostics;

    /** 接收已经校验并冻结的结果状态。 */
    private CompilationResult(T compiled, List<RuleDiagnostic> diagnostics) {
        this.compiled = compiled;
        this.diagnostics = diagnostics;
    }

    /**
     * 创建成功结果；成功结果可以携带非错误诊断。
     *
     * @param compiled 非空编译产物
     * @param diagnostics 非错误诊断
     * @param <T> 编译产物类型
     * @return 成功结果
     */
    public static <T> CompilationResult<T> success(
            T compiled, List<RuleDiagnostic> diagnostics) {
        if (compiled == null) throw RuleEngineException.invalidArgument();
        List<RuleDiagnostic> snapshot = copy(diagnostics);
        if (snapshot.stream().anyMatch(value -> value.severity() == DiagnosticSeverity.ERROR)) {
            throw RuleEngineException.invalidArgument();
        }
        return new CompilationResult<>(compiled, snapshot);
    }

    /**
     * 创建只携带错误诊断的失败结果。
     *
     * @param diagnostics 至少一个错误诊断
     * @param <T> 编译产物类型
     * @return 失败结果
     */
    public static <T> CompilationResult<T> failure(List<RuleDiagnostic> diagnostics) {
        List<RuleDiagnostic> snapshot = copy(diagnostics);
        if (snapshot.isEmpty()
                || snapshot.stream().noneMatch(value -> value.severity() == DiagnosticSeverity.ERROR)) {
            throw RuleEngineException.invalidArgument();
        }
        return new CompilationResult<>(null, snapshot);
    }

    /** @return 存在编译产物时返回 {@code true} */
    public boolean isSuccessful() {
        return compiled != null;
    }

    /** @return 不可变诊断列表 */
    public List<RuleDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * 要求成功并返回编译产物。
     *
     * @return 非空编译产物
     * @throws RuleEngineException 当前结果失败时抛出
     */
    public T requireCompiled() {
        if (compiled == null) throw RuleEngineException.invalidArgument();
        return compiled;
    }

    /** 在容量边界内校验并冻结诊断列表。 */
    private static List<RuleDiagnostic> copy(List<RuleDiagnostic> source) {
        if (source == null) throw RuleEngineException.invalidArgument();
        try {
            List<RuleDiagnostic> result = new ArrayList<>(Math.min(source.size(), MAX_DIAGNOSTICS));
            for (RuleDiagnostic diagnostic : source) {
                if (result.size() == MAX_DIAGNOSTICS || diagnostic == null) {
                    throw RuleEngineException.invalidArgument();
                }
                result.add(diagnostic);
            }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }
}
