package com.github.leyland.letool.ruleengine.spi;

import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace;
import com.github.leyland.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.fact.FactValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionEvaluationResultFactoryTest {

    @Test
    @DisplayName("外部求值器实现应能构造成功和失败结果")
    void shouldAllowExternalEvaluatorToCreateResults() {
        RuleEngineException cause = RuleEngineException.evaluationFailed(
                new IllegalStateException("internal"));
        RuleDiagnostic diagnostic = runtimeError();

        ExpressionEvaluationResult success = ExpressionEvaluationResult.success(
                FactValues.booleanValue(true), EvaluationTrace.disabled());
        ExpressionEvaluationResult failure = ExpressionEvaluationResult.failure(
                List.of(diagnostic), EvaluationTrace.disabled(), cause);

        assertThat(success.requireBoolean()).isTrue();
        assertThat(failure.isSuccessful()).isFalse();
        assertThat(failure.failureCause()).isSameAs(cause);
        assertThatThrownBy(failure::requireValue).isSameAs(cause);
    }

    @Test
    @DisplayName("公开结果工厂应拒绝空组件和非运行期错误诊断")
    void shouldRejectInvalidFactoryArguments() {
        RuleEngineException cause = RuleEngineException.evaluationFailed(
                new IllegalStateException("internal"));
        RuleDiagnostic runtimeWarning = new RuleDiagnostic(
                RuleDiagnosticCode.EVALUATION_ERROR, DiagnosticSeverity.WARNING,
                DiagnosticPhase.RUNTIME, 0, 1, List.of(), null);
        RuleDiagnostic semanticError = new RuleDiagnostic(
                RuleDiagnosticCode.OPERATOR_TYPE_MISMATCH, DiagnosticSeverity.ERROR,
                DiagnosticPhase.SEMANTIC, 0, 1, List.of(), null);

        assertInvalid(() -> ExpressionEvaluationResult.success(
                null, EvaluationTrace.disabled()));
        assertInvalid(() -> ExpressionEvaluationResult.success(
                FactValues.integer(1), null));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                null, EvaluationTrace.disabled(), cause));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                List.of(runtimeError()), null, cause));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                List.of(runtimeError()), EvaluationTrace.disabled(), null));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                List.of(), EvaluationTrace.disabled(), cause));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                List.of(runtimeWarning), EvaluationTrace.disabled(), cause));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                List.of(semanticError), EvaluationTrace.disabled(), cause));
        assertInvalid(() -> ExpressionEvaluationResult.failure(
                java.util.Arrays.asList(runtimeError(), null),
                EvaluationTrace.disabled(), cause));
    }

    @Test
    @DisplayName("失败工厂应防御复制诊断列表")
    void shouldDefensivelyCopyDiagnostics() {
        List<RuleDiagnostic> source = new ArrayList<>();
        source.add(runtimeError());

        ExpressionEvaluationResult result = ExpressionEvaluationResult.failure(
                source, EvaluationTrace.disabled(), RuleEngineException.evaluationFailed(
                        new IllegalStateException("internal")));
        source.clear();

        assertThat(result.diagnostics()).hasSize(1).isUnmodifiable();
    }

    private static RuleDiagnostic runtimeError() {
        return new RuleDiagnostic(RuleDiagnosticCode.EVALUATION_ERROR,
                DiagnosticSeverity.ERROR, DiagnosticPhase.RUNTIME,
                0, 1, List.of(), null);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuleEngineException.class)
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
    }
}
