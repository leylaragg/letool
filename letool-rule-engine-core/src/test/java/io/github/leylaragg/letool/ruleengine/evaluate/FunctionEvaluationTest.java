package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.compile.DefaultExpressionCompiler;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.*;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionEvaluationTest {

    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private static final TypeDescriptor STRING = TypeDescriptor.scalar(TypeKind.STRING, false);
    private static final FunctionSignature NO_ARGS = FunctionSignature.empty();
    private final ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();
    private final FactContract emptyContract = FactContract.builder("empty").build();
    private final RuleFacts noFacts = RuleFacts.fromMap(Map.of());

    @Test
    @DisplayName("逻辑短路不得调用未执行分支中的函数")
    void shouldShortCircuitFunctionCalls() {
        AtomicInteger invocations = new AtomicInteger();
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("COUNT", BOOLEAN, (arguments, context) -> {
                    invocations.incrementAndGet();
                    return FactValues.booleanValue(true);
                })).build();

        assertThat(evaluate("false AND $COUNT()", registry, EngineLimits.defaults())
                .requireBoolean()).isFalse();
        assertThat(evaluate("true OR $COUNT()", registry, EngineLimits.defaults())
                .requireBoolean()).isTrue();
        assertThat(invocations).hasValue(0);
    }

    @Test
    @DisplayName("函数调用次数超限应返回限制错误和安全诊断")
    void shouldReturnFailureWhenFunctionLimitExceeded() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("ONE", INTEGER, (arguments, context) -> FactValues.integer(1)))
                .build();
        EngineLimits limits = new EngineLimits(100, 100, 20, 1, 100, 100);

        ExpressionEvaluationResult result = evaluate("$ONE() + $ONE()", registry, limits);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureCause().getErrorCode())
                .isSameAs(RuleEngineErrorCode.FUNCTION_CALL_LIMIT_EXCEEDED);
        assertThat(result.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED);
        assertThat(result.diagnostics().get(0).startPosition()).isEqualTo(9);
        assertThat(result.diagnostics().get(0).arguments()).containsExactly("ONE");
    }

    @Test
    @DisplayName("函数异常应保留原因链但不把敏感消息写入诊断")
    void shouldWrapFunctionExceptionWithoutLeakingMessage() {
        IllegalStateException cause = new IllegalStateException("token-secret-123");
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("FAIL", INTEGER, (arguments, context) -> { throw cause; }))
                .build();

        ExpressionEvaluationResult result = evaluate("$FAIL()", registry, EngineLimits.defaults());

        assertThat(result.failureCause().getErrorCode()).isSameAs(RuleEngineErrorCode.EVALUATION_FAILED);
        assertThat(result.failureCause().getCause()).isSameAs(cause);
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(RuleDiagnosticCode.FUNCTION_EXECUTION_ERROR);
            assertThat(diagnostic.arguments()).containsExactly("FAIL");
            assertThat(diagnostic.arguments().toString()).doesNotContain("token-secret-123");
        });
    }

    @Test
    @DisplayName("函数返回值应重新验证描述符和编译期类型")
    void shouldRejectWrongFunctionReturnType() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("WRONG", INTEGER,
                        (arguments, context) -> FactValues.string("not-an-integer")))
                .build();

        ExpressionEvaluationResult result = evaluate("$WRONG()", registry, EngineLimits.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
        assertThat(result.diagnostics().get(0).arguments()).containsExactly("WRONG");
    }

    @Test
    @DisplayName("函数返回Java null应作为类型失败且不得从顶层泄漏")
    void shouldRejectJavaNullFunctionReturn() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("NULL_RETURN", INTEGER, (arguments, context) -> null))
                .build();

        ExpressionEvaluationResult result = evaluate(
                "$NULL_RETURN()", registry, EngineLimits.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("函数抛出的框架异常也应作为底层原因重新包装")
    void shouldWrapRuleEngineExceptionThrownByFunction() {
        io.github.leylaragg.letool.ruleengine.exception.RuleEngineException original =
                io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.invalidArgument();
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("FRAMEWORK_FAIL", INTEGER,
                        (arguments, context) -> { throw original; })).build();

        ExpressionEvaluationResult result = evaluate(
                "$FRAMEWORK_FAIL()", registry, EngineLimits.defaults());

        assertThat(result.failureCause()).isNotSameAs(original);
        assertThat(result.failureCause().getCause()).isSameAs(original);
        assertThat(result.diagnostics().get(0).arguments()).containsExactly("FRAMEWORK_FAIL");
    }

    @Test
    @DisplayName("函数抛出的Error不得被求值器吞掉或转换成失败结果")
    void shouldNotCatchFunctionError() {
        AssertionError fatal = new AssertionError("fatal");
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("FATAL", INTEGER,
                        (arguments, context) -> { throw fatal; })).build();
        CompiledExpression expression = compile("$FATAL()", registry, EngineLimits.defaults());

        assertThatThrownBy(() -> evaluator.evaluate(expression, noFacts, registry,
                EvaluationOptions.defaults())).isSameAs(fatal);
    }

    @Test
    @DisplayName("函数上下文只应包含安全事实、区域、时区和固定调用元数据")
    void shouldProvideBoundedInvocationContext() {
        AtomicInteger index = new AtomicInteger();
        FunctionRegistry registry = FunctionRegistry.builder().register(function(
                "CONTEXT", INTEGER, (arguments, context) -> {
                    assertThat(context.locale()).isEqualTo(Locale.CHINA);
                    assertThat(context.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
                    assertThat(context.invocationMetadata()).containsOnlyKeys(
                            "expressionFingerprint", "functionCode", "invocationIndex");
                    assertThat(context.invocationMetadata().get("functionCode")).isEqualTo("CONTEXT");
                    return FactValues.integer(index.incrementAndGet());
                })).build();
        EvaluationOptions options = EvaluationOptions.of(
                Locale.CHINA, ZoneId.of("Asia/Shanghai"), false, EngineLimits.defaults());

        ExpressionEvaluationResult result = evaluator.evaluate(
                compile("$CONTEXT()", registry, EngineLimits.defaults()),
                noFacts, registry, options);

        assertThat(result.requireValue().toSafeJavaValue()).isEqualTo(java.math.BigInteger.ONE);
    }

    private ExpressionEvaluationResult evaluate(
            String source, FunctionRegistry registry, EngineLimits limits) {
        return evaluator.evaluate(compile(source, registry, limits), noFacts, registry,
                EvaluationOptions.of(Locale.ROOT, ZoneId.of("UTC"), false, limits));
    }

    private CompiledExpression compile(String source, FunctionRegistry registry, EngineLimits limits) {
        return new DefaultExpressionCompiler().compile(source, emptyContract, registry, limits)
                .requireCompiled();
    }

    private static final TypeDescriptor BOOLEAN = TypeDescriptor.scalar(TypeKind.BOOLEAN, false);

    private static RuleFunction function(
            String code, TypeDescriptor returnType, FunctionBody body) {
        return new RuleFunction() {
            @Override public String code() { return code; }
            @Override public String semanticVersion() { return "1"; }
            @Override public FunctionSignature signature() { return NO_ARGS; }
            @Override public TypeDescriptor returnType() { return returnType; }
            @Override public FunctionCharacteristics characteristics() {
                return FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                        FunctionEffect.CONTEXTUAL, FunctionThreading.THREAD_SAFE);
            }
            @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
                return body.execute(arguments, context);
            }
        };
    }

    /** 测试函数体。 */
    private interface FunctionBody {
        FactValue execute(FunctionArguments arguments, FunctionContext context);
    }
}
