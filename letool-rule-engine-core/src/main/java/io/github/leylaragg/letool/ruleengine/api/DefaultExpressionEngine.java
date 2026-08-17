package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.compile.DefaultExpressionCompiler;
import io.github.leylaragg.letool.ruleengine.compile.ExpressionCompiler;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationTrace;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluator;
import io.github.leylaragg.letool.ruleengine.evaluate.RuntimeFactValidator;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeCompatibility;

import java.util.List;

/**
 * 固化编译器、求值器、限制与函数目录快照的默认表达式引擎。
 */
public final class DefaultExpressionEngine implements ExpressionEngine {

    /** 固化在引擎快照中的编译器。 */
    private final ExpressionCompiler compiler;

    /** 固化在引擎快照中的求值器。 */
    private final ExpressionEvaluator evaluator;

    /** 编译及门面治理使用的资源上限。 */
    private final EngineLimits limits;

    /** 构建时冻结的函数目录。 */
    private final FunctionRegistry functionRegistry;

    /**
     * 创建与构建器后续修改隔离的引擎快照。
     *
     * @param compiler 编译器
     * @param evaluator 求值器
     * @param limits 资源上限
     * @param functionRegistry 函数目录快照
     */
    DefaultExpressionEngine(ExpressionCompiler compiler, ExpressionEvaluator evaluator,
            EngineLimits limits, FunctionRegistry functionRegistry) {
        if (compiler == null || evaluator == null || limits == null || functionRegistry == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.compiler = compiler;
        this.evaluator = evaluator;
        this.limits = limits;
        this.functionRegistry = functionRegistry;
    }

    /** {@inheritDoc} */
    @Override
    public CompilationResult<CompiledExpression> compile(String source, FactContract factContract) {
        if (source == null || factContract == null) throw RuleEngineException.invalidArgument();
        try {
            CompilationResult<CompiledExpression> result = compiler.compile(
                    source, factContract, functionRegistry, limits);
            if (result == null) throw new IllegalStateException("null compiler result");
            return result;
        } catch (RuleEngineException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RuleEngineException.compilationFailed(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ExpressionEvaluationResult evaluate(
            CompiledExpression expression, RuleFacts facts, EvaluationOptions options) {
        if (expression == null || facts == null || options == null) {
            throw RuleEngineException.invalidArgument();
        }
        String mismatchedDimension = mismatch(expression);
        if (mismatchedDimension != null) return fingerprintFailure(expression, mismatchedDimension);
        ExpressionEvaluationResult factFailure = RuntimeFactValidator.validate(expression, facts);
        if (factFailure != null) return factFailure;
        EvaluationOptions effectiveOptions = EvaluationOptions.of(
                options.locale(), options.zoneId(), options.traceEnabled(),
                EngineLimits.stricterOf(limits, options.limits()));
        try {
            ExpressionEvaluationResult result = evaluator.evaluate(
                    expression, facts, functionRegistry, effectiveOptions);
            if (result == null) throw new IllegalStateException("null evaluator result");
            if (result.isSuccessful() && !RuntimeFactValidator.isAssignable(
                    result.requireValue(), expression.resultType())) {
                return resultTypeFailure(expression);
            }
            return result;
        } catch (RuntimeException exception) {
            return evaluatorFailure(expression, exception);
        }
    }

    /** 返回首个不兼容的编译环境维度，全部兼容时返回 {@code null}。 */
    private String mismatch(CompiledExpression expression) {
        if (!functionRegistry.fingerprint().equals(expression.functionCatalogFingerprint())) {
            return "functionCatalogFingerprint";
        }
        if (!TypeCompatibility.TYPE_CATALOG_FINGERPRINT.equals(
                expression.typeCatalogFingerprint())) return "typeCatalogFingerprint";
        if (!DefaultExpressionCompiler.ENGINE_VERSION.equals(expression.engineVersion())) {
            return "engineVersion";
        }
        if (!DefaultExpressionCompiler.LANGUAGE_VERSION.equals(expression.languageVersion())) {
            return "languageVersion";
        }
        return null;
    }

    /** 将编译环境不匹配转换为不泄露内部细节的运行期诊断。 */
    private static ExpressionEvaluationResult fingerprintFailure(
            CompiledExpression expression, String dimension) {
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.FINGERPRINT_MISMATCH,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.RUNTIME,
                0,
                expression.source().length(),
                List.of(dimension),
                null);
        return ExpressionEvaluationResult.failure(List.of(diagnostic), EvaluationTrace.disabled(),
                RuleEngineException.evaluationFailed(
                        new IllegalStateException("compiled expression environment mismatch")));
    }

    /** 净化宿主求值器抛出的运行时异常，同时在结果中保留原因链。 */
    private static ExpressionEvaluationResult evaluatorFailure(
            CompiledExpression expression, RuntimeException cause) {
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.EVALUATION_ERROR,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.RUNTIME,
                0,
                expression.source().length(),
                List.of(),
                null);
        return ExpressionEvaluationResult.failure(List.of(diagnostic), EvaluationTrace.disabled(),
                RuleEngineException.evaluationFailed(cause));
    }

    /** 拒绝自定义求值器返回的契约外类型。 */
    private static ExpressionEvaluationResult resultTypeFailure(CompiledExpression expression) {
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.RUNTIME,
                expression.ast().startPosition(),
                expression.ast().endPosition(),
                List.of(),
                null);
        return ExpressionEvaluationResult.failure(List.of(diagnostic), EvaluationTrace.disabled(),
                RuleEngineException.evaluationFailed(
                        new IllegalStateException("custom evaluator result type mismatch")));
    }
}
