package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompilationRequest;
import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationRequest;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.evaluate.ValueSummarizer;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.internal.EngineExecutionModelFactory;

import java.util.List;

/**
 * 固化编译器、求值器、限制与函数目录快照的默认表达式引擎。
 */
final class DefaultExpressionEngine implements ExpressionEngine {

    /** 由 core 完整拥有的固定编译流水线。 */
    private final ExpressionCompilationPipeline compilationPipeline;

    /** 与编译语义成套发布的固定求值运行时。 */
    private final ExpressionEvaluationRuntime evaluationRuntime;

    /** 编译及门面治理使用的资源上限。 */
    private final EngineLimits limits;

    /** 构建时冻结的函数目录。 */
    private final FunctionRegistry functionRegistry;

    /** 覆盖当前引擎全部编译与求值语义的不可变描述。 */
    private final ExecutionModelDescriptor executionModel;

    /**
     * 创建与构建器后续修改隔离的引擎快照。
     *
     * @param limits 资源上限
     * @param functionRegistry 函数目录快照
     * @param valueSummarizer 安全轨迹摘要策略
     */
    DefaultExpressionEngine(EngineLimits limits, FunctionRegistry functionRegistry,
            ValueSummarizer valueSummarizer) {
        if (limits == null || functionRegistry == null || valueSummarizer == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.compilationPipeline = new ExpressionCompilationPipeline();
        this.evaluationRuntime = new ExpressionEvaluationRuntime(valueSummarizer);
        this.limits = limits;
        this.functionRegistry = functionRegistry;
        this.executionModel = EngineExecutionModelFactory.create(limits, functionRegistry);
    }

    /** {@inheritDoc} */
    @Override
    public CompilationResult<CompiledExpression> compile(CompilationRequest request) {
        if (request == null) throw RuleEngineException.invalidArgument();
        try {
            CompilationResult<CompiledExpression> result = compilationPipeline.compile(
                    request.source(), request.factContract(), functionRegistry, limits,
                    executionModel);
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
    public ExpressionEvaluationResult evaluate(EvaluationRequest request) {
        if (request == null) throw RuleEngineException.invalidArgument();
        CompiledExpression expression = request.expression();
        RuleFacts facts = request.facts();
        EvaluationOptions options = request.options();
        String mismatchedDimension = mismatch(expression);
        if (mismatchedDimension != null) {
            return environmentMismatchFailure(expression, mismatchedDimension);
        }
        ExpressionEvaluationResult factFailure = RuntimeFactValidator.validate(expression, facts);
        if (factFailure != null) return factFailure;
        EvaluationOptions effectiveOptions = EvaluationOptions.of(
                options.locale(), options.zoneId(), options.traceEnabled(),
                EngineLimits.stricterOf(limits, options.limits()));
        try {
            ExpressionEvaluationResult result = evaluationRuntime.evaluate(
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

    /** {@inheritDoc} */
    @Override
    public ExecutionModelDescriptor executionModel() {
        return executionModel;
    }

    /** 返回首个不兼容的编译环境维度，全部兼容时返回 {@code null}。 */
    private String mismatch(CompiledExpression expression) {
        return executionModel.environmentDigest().equals(expression.environmentDigest())
                ? null : "environmentDigest";
    }

    /** 将编译环境不匹配转换为不泄露内部细节的运行期诊断。 */
    private static ExpressionEvaluationResult environmentMismatchFailure(
            CompiledExpression expression, String dimension) {
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.EXECUTION_ENVIRONMENT_MISMATCH,
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

    /** 净化求值运行时抛出的异常，同时在结果中保留原因链。 */
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

    /** 拒绝违反编译期结果类型不变量的运行时值。 */
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
                        new IllegalStateException("evaluation result type mismatch")));
    }
}
