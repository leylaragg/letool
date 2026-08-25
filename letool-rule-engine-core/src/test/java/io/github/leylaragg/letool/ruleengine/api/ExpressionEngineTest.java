package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionParameter;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("表达式引擎公开门面")
class ExpressionEngineTest {

    private static final TypeDescriptor DATE = TypeDescriptor.scalar(TypeKind.DATE, false);
    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private static final FactContract BIRTHDAY_CONTRACT = FactContract.builder("customer-v1")
            .path("birthday", DATE)
            .build();

    @Test
    @DisplayName("纯 Java 门面应完成函数注册、编译和求值")
    void shouldCompileAndEvaluateWithoutSpring() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 8, 14).atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        ExpressionEngine engine = ExpressionEngine.builder()
                .registerFunction(new AgeFunction(fixedClock))
                .limits(EngineLimits.defaults())
                .build();

        CompilationResult<CompiledExpression> compilation = engine.compile(
                "$AGE(${birthday}) >= 18", BIRTHDAY_CONTRACT);

        assertThat(compilation.isSuccessful()).isTrue();
        ExpressionEvaluationResult result = engine.evaluate(
                compilation.requireCompiled(),
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.defaults());
        assertThat(result.requireBoolean()).isTrue();
    }

    @Test
    @DisplayName("构建器快照不得被后续注册修改")
    void shouldFreezeRegistrySnapshotAtBuildTime() {
        ExpressionEngineBuilder builder = ExpressionEngine.builder();
        ExpressionEngine first = builder.build();
        builder.registerFunction(new AgeFunction(Clock.systemUTC()));
        ExpressionEngine second = builder.build();

        assertThat(first.compile("$AGE(${birthday})", BIRTHDAY_CONTRACT).isSuccessful()).isFalse();
        assertThat(second.compile("$AGE(${birthday})", BIRTHDAY_CONTRACT).isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("构建器应拒绝空依赖和重复函数")
    void shouldRejectNullDependenciesAndDuplicateFunctions() {
        AgeFunction function = new AgeFunction(Clock.systemUTC());
        ExpressionEngineBuilder builder = ExpressionEngine.builder().registerFunction(function);

        assertThatThrownBy(() -> builder.registerFunction(function))
                .isInstanceOf(RuleEngineException.class)
                .extracting(error -> ((RuleEngineException) error).getErrorCode())
                .isEqualTo(RuleEngineErrorCode.REGISTRATION_CONFLICT);
        assertThatThrownBy(() -> ExpressionEngine.builder().limits(null))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> ExpressionEngine.builder().valueSummarizer(null))
                .isInstanceOf(RuleEngineException.class);
    }

    @Test
    @DisplayName("宿主摘要策略只影响轨迹展示")
    void shouldApplyHostValueSummarizerOnlyToTrace() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .valueSummarizer((value, maximumLength) -> "MASKED")
                .build();
        CompiledExpression compiled = engine.compile(
                "${birthday} IS NOT NULL", BIRTHDAY_CONTRACT).requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(
                compiled,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.of(java.util.Locale.ROOT, ZoneOffset.UTC, true,
                        EngineLimits.defaults()));

        assertThat(result.requireBoolean()).isTrue();
        assertThat(result.trace().nodes())
                .extracting(node -> node.summary())
                .containsOnly("MASKED");
    }

    @Test
    @DisplayName("门面应在求值前校验运行事实契约")
    void shouldValidateRuntimeFactContractBeforeEvaluation() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        CompiledExpression compiled = engine.compile("${birthday} IS NOT NULL", BIRTHDAY_CONTRACT)
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", "not-a-date", "ignored", "safe")),
                EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("门面应以依赖范围报告缺失事实")
    void shouldReportMissingFactFromDependencyRange() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        CompiledExpression compiled = engine.compile(
                "${birthday} IS NOT NULL", BIRTHDAY_CONTRACT).requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(RuleDiagnosticCode.MISSING_FACT_VALUE);
            assertThat(diagnostic.startPosition()).isZero();
            assertThat(diagnostic.endPosition()).isEqualTo("${birthday}".length());
        });
    }

    @Test
    @DisplayName("门面只应检查编译产物引用的事实路径")
    void shouldIgnoreUnrelatedFactsDuringRuntimeValidation() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        CompiledExpression compiled = engine.compile("${birthday} IS NOT NULL", BIRTHDAY_CONTRACT)
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(compiled,
                RuleFacts.fromMap(Map.of(
                        "birthday", LocalDate.of(2000, 1, 1),
                        "unrelated", Map.of("value", "ignored"))),
                EvaluationOptions.defaults());

        assertThat(result.requireBoolean()).isTrue();
    }

    @Test
    @DisplayName("环境摘要不匹配时应在求值前返回失败")
    void shouldRejectFunctionCatalogMismatchBeforeEvaluation() {
        ExpressionEngine sourceEngine = ExpressionEngine.builder().build();
        CompiledExpression compiled = sourceEngine.compile(
                "${birthday} IS NOT NULL", BIRTHDAY_CONTRACT).requireCompiled();
        ExpressionEngine targetEngine = ExpressionEngine.builder()
                .registerFunction(new AgeFunction(Clock.systemUTC()))
                .build();

        ExpressionEvaluationResult result = targetEngine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code())
                            .isEqualTo(RuleDiagnosticCode.EXECUTION_ENVIRONMENT_MISMATCH);
                    assertThat(diagnostic.arguments()).containsExactly("environmentDigest");
                    assertThat(diagnostic.startPosition()).isZero();
                    assertThat(diagnostic.endPosition()).isEqualTo(compiled.source().length());
                });
    }

    @Test
    @DisplayName("每次构建工厂快照只应执行一次元数据探测")
    void shouldProbeInvocationFactoryExactlyOncePerBuild() {
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger descriptorReads = new AtomicInteger();
        RuleFunction prototype = new InvocationAgeFunction(Clock.systemUTC());
        RuleFunctionFactory factory = new RuleFunctionFactory() {
            @Override public FunctionDescriptor descriptor() {
                descriptorReads.incrementAndGet();
                return FunctionDescriptor.from(prototype);
            }
            @Override public RuleFunction create() {
                probes.incrementAndGet();
                return new InvocationAgeFunction(Clock.systemUTC());
            }
        };
        ExpressionEngineBuilder builder = ExpressionEngine.builder().registerFunction(factory);
        assertThat(descriptorReads).hasValue(1);

        ExpressionEngine first = builder.build();

        assertThat(probes).hasValue(1);
        assertThat(descriptorReads).hasValue(1);
        assertThat(first.compile("$AGE(${birthday})", BIRTHDAY_CONTRACT).isSuccessful()).isTrue();
        assertThat(probes).hasValue(1);
        builder.build();
        assertThat(probes).hasValue(2);
        assertThat(descriptorReads).hasValue(1);
    }

    @Test
    @DisplayName("直接函数元数据应在注册时精确读取一次并冻结")
    void shouldFreezeDirectFunctionMetadataAtRegistration() {
        AtomicInteger codeReads = new AtomicInteger();
        AtomicInteger versionReads = new AtomicInteger();
        AtomicInteger signatureReads = new AtomicInteger();
        AtomicInteger returnTypeReads = new AtomicInteger();
        AtomicInteger characteristicsReads = new AtomicInteger();
        AtomicBoolean mutated = new AtomicBoolean();
        RuleFunction mutableFunction = new RuleFunction() {
            @Override public String code() {
                codeReads.incrementAndGet();
                return mutated.get() ? "CHANGED" : "STABLE";
            }
            @Override public String semanticVersion() {
                versionReads.incrementAndGet();
                return mutated.get() ? "2" : "1";
            }
            @Override public FunctionSignature signature() {
                signatureReads.incrementAndGet();
                return mutated.get() ? FunctionSignature.empty()
                        : FunctionSignature.of(FunctionParameter.required("value", INTEGER));
            }
            @Override public TypeDescriptor returnType() {
                returnTypeReads.incrementAndGet();
                return mutated.get() ? TypeDescriptor.scalar(TypeKind.STRING, false) : INTEGER;
            }
            @Override public FunctionCharacteristics characteristics() {
                characteristicsReads.incrementAndGet();
                return FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                        mutated.get() ? FunctionEffect.CONTEXTUAL : FunctionEffect.PURE,
                        FunctionThreading.THREAD_SAFE);
            }
            @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
                return arguments.get(0);
            }
        };

        ExpressionEngineBuilder builder = ExpressionEngine.builder().registerFunction(mutableFunction);
        mutated.set(true);
        ExpressionEngine first = builder.build();
        ExpressionEngine second = builder.build();
        FactContract contract = FactContract.builder("amount-stable-v1")
                .path("amount", INTEGER).build();
        CompiledExpression firstCompiled = first.compile("$STABLE(${amount})", contract)
                .requireCompiled();
        CompiledExpression secondCompiled = second.compile("$STABLE(${amount})", contract)
                .requireCompiled();

        assertThat(firstCompiled.environmentDigest())
                .isEqualTo(secondCompiled.environmentDigest());
        assertThat(first.evaluate(firstCompiled, RuleFacts.fromMap(Map.of("amount", 9)),
                EvaluationOptions.defaults()).requireValue().toSafeJavaValue())
                .isEqualTo(java.math.BigInteger.valueOf(9));
        assertThat(codeReads).hasValue(1);
        assertThat(versionReads).hasValue(1);
        assertThat(signatureReads).hasValue(1);
        assertThat(returnTypeReads).hasValue(1);
        assertThat(characteristicsReads).hasValue(1);
    }

    @Test
    @DisplayName("公开门面应拒绝空调用参数")
    void shouldRejectNullApiArguments() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        CompiledExpression compiled = engine.compile("true", FactContract.builder("empty").build())
                .requireCompiled();

        assertThatThrownBy(() -> engine.compile(null, BIRTHDAY_CONTRACT))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> engine.compile("true", null))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> engine.evaluate(null, RuleFacts.fromMap(Map.of()),
                EvaluationOptions.defaults())).isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> engine.evaluate(compiled, null, EvaluationOptions.defaults()))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> engine.evaluate(compiled, RuleFacts.fromMap(Map.of()), null))
                .isInstanceOf(RuleEngineException.class);
    }

    @Test
    @DisplayName("同一门面应支持一千次并发编译和求值")
    void shouldCompileAndEvaluateConcurrently() throws Exception {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        FactContract contract = FactContract.builder("amount-v1").path("amount", INTEGER).build();
        CompiledExpression compiled = engine.compile("${amount} >= 18", contract).requireCompiled();
        var executor = Executors.newFixedThreadPool(12);
        try {
            var tasks = java.util.stream.IntStream.range(0, 1_000)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        CompiledExpression current = engine.compile("${amount} >= 18", contract)
                                .requireCompiled();
                        return current.equals(compiled) && engine.evaluate(current,
                                RuleFacts.fromMap(Map.of("amount", index)),
                                EvaluationOptions.defaults()).requireBoolean() == (index >= 18);
                    }).toList();
            assertThat(executor.invokeAll(tasks)).allSatisfy(future -> assertThat(future.get()).isTrue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("单次求值选项不得放宽引擎函数调用限制")
    void shouldNotRelaxEngineFunctionLimitAtEvaluationTime() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .registerFunction(new AgeFunction(Clock.systemUTC()))
                .limits(new EngineLimits(1_000, 1_000, 100, 1, 100, 100))
                .build();
        CompiledExpression compiled = engine.compile(
                "$AGE(${birthday}) + $AGE(${birthday}) > 0", BIRTHDAY_CONTRACT)
                .requireCompiled();
        EvaluationOptions permissive = EvaluationOptions.of(
                java.util.Locale.ROOT, java.time.ZoneId.of("UTC"), false,
                new EngineLimits(10_000, 10_000, 1_000, 1_000, 1_000, 1_000));

        ExpressionEvaluationResult result = engine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))), permissive);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                .containsExactly(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("引擎轨迹节点和摘要边界不能被请求放宽且请求可进一步收紧")
    void shouldApplyStricterTraceAndSummaryLimits() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .limits(new EngineLimits(1_000, 1_000, 100, 100, 2, 4))
                .build();
        CompiledExpression compiled = engine.compile("'abcdefgh' = 'abcdefgh'",
                FactContract.builder("empty").build()).requireCompiled();
        EvaluationOptions permissive = EvaluationOptions.of(java.util.Locale.ROOT,
                java.time.ZoneId.of("UTC"), true,
                new EngineLimits(2_000, 2_000, 200, 200, 200, 200));
        EvaluationOptions stricter = EvaluationOptions.of(java.util.Locale.ROOT,
                java.time.ZoneId.of("UTC"), true,
                new EngineLimits(2_000, 2_000, 200, 200, 1, 2));

        ExpressionEvaluationResult bounded = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), permissive);
        ExpressionEvaluationResult tightened = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), stricter);

        assertThat(bounded.trace().nodes()).hasSize(2).allSatisfy(node ->
                assertThat(node.summary()).hasSizeLessThanOrEqualTo(4));
        assertThat(tightened.trace().nodes()).hasSize(1).allSatisfy(node ->
                assertThat(node.summary()).hasSizeLessThanOrEqualTo(2));
    }

    private static class AgeFunction implements RuleFunction {
        private final Clock clock;

        private AgeFunction(Clock clock) {
            this.clock = clock;
        }

        @Override public String code() { return "AGE"; }
        @Override public String semanticVersion() { return "1"; }
        @Override public FunctionSignature signature() {
            return FunctionSignature.of(FunctionParameter.required("birthday", DATE));
        }
        @Override public TypeDescriptor returnType() { return INTEGER; }
        @Override public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE, FunctionThreading.THREAD_SAFE);
        }
        @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            LocalDate birthday = (LocalDate) arguments.get(0).toSafeJavaValue();
            return FactValues.integer(Period.between(
                    birthday, LocalDate.now(clock.withZone(context.zoneId()))).getYears());
        }
    }

    private static final class InvocationAgeFunction extends AgeFunction {
        private InvocationAgeFunction(Clock clock) { super(clock); }
        @Override public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE, FunctionThreading.INVOCATION_SCOPED);
        }
    }
}
