package com.github.leyland.letool.ruleengine.evaluate;

import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.ExpressionDependency;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.fact.FactResolver;
import com.github.leyland.letool.ruleengine.fact.FactValue;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;

import java.util.List;

/**
 * 按编译产物的类型化依赖验证运行事实，不遍历无关事实。
 */
public final class RuntimeFactValidator {

    private RuntimeFactValidator() {
    }

    /**
     * 验证表达式引用的所有事实依赖。
     *
     * @param expression 已编译表达式
     * @param facts 运行事实快照
     * @return 首个失败结果；全部兼容时为 {@code null}
     */
    public static ExpressionEvaluationResult validate(
            CompiledExpression expression, RuleFacts facts) {
        if (expression == null || facts == null) throw RuleEngineException.invalidArgument();
        FactResolver resolver = new FactResolver();
        for (ExpressionDependency dependency : expression.dependencies().values()) {
            FactValue value;
            try {
                value = resolver.resolve(facts, dependency.path()).orElse(null);
            } catch (RuleEngineException exception) {
                return failure(dependency, RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH, exception);
            }
            if (value == null) {
                return failure(dependency, RuleDiagnosticCode.MISSING_FACT_VALUE,
                        new IllegalStateException("missing fact"));
            }
            if (!FactValueTypes.isAssignable(value, dependency.expectedType())) {
                return failure(dependency, RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH,
                        new IllegalStateException("runtime fact type mismatch"));
            }
        }
        return null;
    }

    /**
     * 判断事实值是否可赋给声明类型。
     *
     * @param value 非空事实值
     * @param expected 声明类型
     * @return 类型与可空性兼容时返回 {@code true}
     */
    public static boolean isAssignable(
            FactValue value,
            com.github.leyland.letool.ruleengine.type.TypeDescriptor expected) {
        if (value == null || expected == null) throw RuleEngineException.invalidArgument();
        return FactValueTypes.isAssignable(value, expected);
    }

    private static ExpressionEvaluationResult failure(ExpressionDependency dependency,
            RuleDiagnosticCode code, Throwable cause) {
        String path = dependency.path().toString();
        int maximum = RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH;
        if (path.length() > maximum) path = path.substring(0, maximum);
        RuleDiagnostic diagnostic = new RuleDiagnostic(code, DiagnosticSeverity.ERROR,
                DiagnosticPhase.RUNTIME, dependency.startPosition(), dependency.endPosition(),
                List.of(path), null);
        RuleEngineException frameworkException = cause instanceof RuleEngineException exception
                ? exception : RuleEngineException.evaluationFailed(cause);
        return ExpressionEvaluationResult.failure(List.of(diagnostic),
                EvaluationTrace.disabled(), frameworkException);
    }
}
