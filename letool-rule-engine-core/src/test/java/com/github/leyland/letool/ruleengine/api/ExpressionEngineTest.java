package com.github.leyland.letool.ruleengine.api;

import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.CompilationResult;
import com.github.leyland.letool.ruleengine.compile.CompiledExpressionFixtures;
import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.compile.ExpressionCompiler;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.evaluate.DefaultExpressionEvaluator;
import com.github.leyland.letool.ruleengine.evaluate.EvaluationOptions;
import com.github.leyland.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import com.github.leyland.letool.ruleengine.evaluate.ExpressionEvaluator;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.fact.FactValue;
import com.github.leyland.letool.ruleengine.fact.FactValues;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;
import com.github.leyland.letool.ruleengine.function.FunctionArguments;
import com.github.leyland.letool.ruleengine.function.FunctionCharacteristics;
import com.github.leyland.letool.ruleengine.function.FunctionContext;
import com.github.leyland.letool.ruleengine.function.FunctionDeterminism;
import com.github.leyland.letool.ruleengine.function.FunctionEffect;
import com.github.leyland.letool.ruleengine.function.FunctionParameter;
import com.github.leyland.letool.ruleengine.function.FunctionSignature;
import com.github.leyland.letool.ruleengine.function.FunctionThreading;
import com.github.leyland.letool.ruleengine.function.RuleFunction;
import com.github.leyland.letool.ruleengine.function.RuleFunctionFactory;
import com.github.leyland.letool.ruleengine.function.FunctionDescriptor;
import com.github.leyland.letool.ruleengine.type.FactContract;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;
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
import java.util.concurrent.ConcurrentHashMap;
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
        assertThatThrownBy(() -> ExpressionEngine.builder().compiler(null))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> ExpressionEngine.builder().evaluator(null))
                .isInstanceOf(RuleEngineException.class);
    }

    @Test
    @DisplayName("构建器应注入自定义编译器和求值器")
    void shouldDelegateToCustomCompilerAndEvaluator() {
        AtomicBoolean compileCalled = new AtomicBoolean();
        AtomicBoolean evaluateCalled = new AtomicBoolean();
        ExpressionCompiler compiler = (source, contract, registry, limits) -> {
            compileCalled.set(true);
            return new DefaultExpressionCompiler().compile(source, contract, registry, limits);
        };
        ExpressionEvaluator evaluator = (expression, facts, registry, options) -> {
            evaluateCalled.set(true);
            return new DefaultExpressionEvaluator().evaluate(expression, facts, registry, options);
        };
        ExpressionEngine engine = ExpressionEngine.builder()
                .compiler(compiler)
                .evaluator(evaluator)
                .build();

        CompiledExpression compiled = engine.compile("${birthday} IS NOT NULL", BIRTHDAY_CONTRACT)
                .requireCompiled();
        ExpressionEvaluationResult result = engine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.defaults());

        assertThat(compileCalled).isTrue();
        assertThat(evaluateCalled).isTrue();
        assertThat(result.requireBoolean()).isTrue();
    }

    @Test
    @DisplayName("门面应在调用自定义求值器前校验运行事实契约")
    void shouldNotLetCustomEvaluatorBypassRuntimeFactContract() {
        AtomicBoolean evaluateCalled = new AtomicBoolean();
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) -> {
                    evaluateCalled.set(true);
                    return ExpressionEvaluationResult.success(
                            FactValues.booleanValue(true),
                            com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace.disabled());
                })
                .build();
        CompiledExpression compiled = engine.compile("${birthday} IS NOT NULL", BIRTHDAY_CONTRACT)
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", "not-a-date", "ignored", "safe")),
                EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
        assertThat(evaluateCalled).isFalse();
    }

    @Test
    @DisplayName("门面应拒绝自定义求值器返回不符合编译结果类型的值")
    void shouldRejectCustomEvaluatorResultTypeMismatch() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) ->
                        ExpressionEvaluationResult.success(FactValues.string("wrong"),
                                com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace.disabled()))
                .build();
        CompiledExpression compiled = engine.compile("true", FactContract.builder("empty").build())
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("门面应以依赖范围报告缺失事实且不调用求值器")
    void shouldReportMissingFactBeforeCustomEvaluation() {
        AtomicBoolean evaluateCalled = new AtomicBoolean();
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) -> {
                    evaluateCalled.set(true);
                    return ExpressionEvaluationResult.success(FactValues.booleanValue(true),
                            com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace.disabled());
                }).build();
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
        assertThat(evaluateCalled).isFalse();
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
    @DisplayName("指纹不匹配时应返回失败且不调用求值器")
    void shouldRejectFunctionCatalogMismatchBeforeEvaluation() {
        ExpressionEngine sourceEngine = ExpressionEngine.builder().build();
        CompiledExpression compiled = sourceEngine.compile(
                "${birthday} IS NOT NULL", BIRTHDAY_CONTRACT).requireCompiled();
        AtomicBoolean evaluateCalled = new AtomicBoolean();
        ExpressionEngine targetEngine = ExpressionEngine.builder()
                .registerFunction(new AgeFunction(Clock.systemUTC()))
                .evaluator((expression, facts, registry, options) -> {
                    evaluateCalled.set(true);
                    return ExpressionEvaluationResult.success(
                            FactValues.booleanValue(true),
                            com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace.disabled());
                })
                .build();

        ExpressionEvaluationResult result = targetEngine.evaluate(compiled,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(RuleDiagnosticCode.FINGERPRINT_MISMATCH);
                    assertThat(diagnostic.arguments()).containsExactly("functionCatalogFingerprint");
                    assertThat(diagnostic.startPosition()).isZero();
                    assertThat(diagnostic.endPosition()).isEqualTo(compiled.source().length());
                });
        assertThat(evaluateCalled).isFalse();
    }

    @ParameterizedTest(name = "拒绝不匹配维度 {0}")
    @MethodSource("mismatchedDimensions")
    @DisplayName("门面应拒绝每一个环境语义维度不匹配")
    void shouldRejectEveryEnvironmentDimensionMismatch(String dimension, String replacement) {
        AtomicBoolean evaluateCalled = new AtomicBoolean();
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) -> {
                    evaluateCalled.set(true);
                    return ExpressionEvaluationResult.success(FactValues.booleanValue(true),
                            com.github.leyland.letool.ruleengine.evaluate.EvaluationTrace.disabled());
                }).build();
        CompiledExpression original = engine.compile(
                "${birthday} IS NOT NULL", BIRTHDAY_CONTRACT).requireCompiled();
        CompiledExpression changed = CompiledExpressionFixtures.withDimension(
                original, dimension, replacement);

        ExpressionEvaluationResult result = engine.evaluate(changed,
                RuleFacts.fromMap(Map.of("birthday", LocalDate.of(2000, 1, 1))),
                EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(RuleDiagnosticCode.FINGERPRINT_MISMATCH);
                    assertThat(diagnostic.arguments()).containsExactly(dimension);
                });
        assertThat(evaluateCalled).isFalse();
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

        assertThat(firstCompiled.functionCatalogFingerprint())
                .isEqualTo(secondCompiled.functionCatalogFingerprint());
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
    @DisplayName("自定义求值器异常应净化为稳定失败结果")
    void shouldSanitizeCustomEvaluatorFailure() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) -> {
                    throw new IllegalStateException("sensitive-evaluator-message");
                }).build();
        CompiledExpression compiled = engine.compile("true", FactContract.builder("empty").build())
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                .containsExactly(RuleDiagnosticCode.EVALUATION_ERROR);
        assertThat(result.diagnostics().get(0).arguments()).isEmpty();
        assertThat(result.failureCause().getMessage()).doesNotContain("sensitive-evaluator-message");
        assertThat(result.failureCause().getCause()).hasMessage("sensitive-evaluator-message");
    }

    @Test
    @DisplayName("自定义求值器抛框架异常也应返回失败结果")
    void shouldConvertCustomEvaluatorFrameworkExceptionToFailure() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .evaluator((expression, facts, registry, options) -> {
                    throw RuleEngineException.invalidArgument();
                }).build();
        CompiledExpression compiled = engine.compile("true", FactContract.builder("empty").build())
                .requireCompiled();

        ExpressionEvaluationResult result = engine.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                .containsExactly(RuleDiagnosticCode.EVALUATION_ERROR);
        assertThat(result.failureCause()).isNotNull();
    }

    @Test
    @DisplayName("自定义编译器异常应包装为稳定框架异常")
    void shouldSanitizeCustomCompilerFailure() {
        ExpressionEngine engine = ExpressionEngine.builder()
                .compiler((source, contract, registry, limits) -> {
                    throw new IllegalStateException("sensitive-compiler-message");
                }).build();

        assertThatThrownBy(() -> engine.compile("true", FactContract.builder("empty").build()))
                .isInstanceOf(RuleEngineException.class)
                .satisfies(error -> {
                    RuleEngineException exception = (RuleEngineException) error;
                    assertThat(exception.getErrorCode().getCode())
                            .isEqualTo("RULE_ENGINE_COMPILE_001");
                    assertThat(exception.getMessage())
                            .isEqualTo("[RULE_ENGINE_COMPILE_001] 规则表达式编译失败");
                    assertThat(error.getMessage()).doesNotContain("sensitive-compiler-message");
                    assertThat(error.getCause()).hasMessage("sensitive-compiler-message");
                });
    }

    private static Stream<Arguments> mismatchedDimensions() {
        return Stream.of(
                Arguments.of("functionCatalogFingerprint", "0".repeat(64)),
                Arguments.of("typeCatalogFingerprint", "0".repeat(64)),
                Arguments.of("engineVersion", "999"),
                Arguments.of("languageVersion", "999"));
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
    @DisplayName("同一引擎注入的自定义编译器和求值器应支持一千次并发调用")
    void shouldInvokeCustomSpisConcurrentlyWithoutStateInterference() throws Exception {
        DefaultExpressionCompiler defaultCompiler = new DefaultExpressionCompiler();
        DefaultExpressionEvaluator defaultEvaluator = new DefaultExpressionEvaluator();
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger evaluateCalls = new AtomicInteger();
        var compileThreads = ConcurrentHashMap.<String>newKeySet();
        var evaluateThreads = ConcurrentHashMap.<String>newKeySet();
        ExpressionCompiler compiler = (source, contract, registry, limits) -> {
            compileCalls.incrementAndGet();
            compileThreads.add(Thread.currentThread().getName());
            return defaultCompiler.compile(source, contract, registry, limits);
        };
        ExpressionEvaluator evaluator = (expression, facts, registry, options) -> {
            evaluateCalls.incrementAndGet();
            evaluateThreads.add(Thread.currentThread().getName());
            return defaultEvaluator.evaluate(expression, facts, registry, options);
        };
        ExpressionEngine engine = ExpressionEngine.builder()
                .compiler(compiler).evaluator(evaluator).build();
        FactContract contract = FactContract.builder("amount-spi-v1")
                .path("amount", INTEGER).build();
        CompiledExpression baseline = defaultCompiler.compile("${amount} >= 18", contract,
                com.github.leyland.letool.ruleengine.function.FunctionRegistry.builder().build(),
                EngineLimits.defaults()).requireCompiled();
        var executor = Executors.newFixedThreadPool(16);
        try {
            var tasks = java.util.stream.IntStream.range(0, 1_000)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        CompiledExpression current = engine.compile("${amount} >= 18", contract)
                                .requireCompiled();
                        return current.equals(baseline) && engine.evaluate(current,
                                RuleFacts.fromMap(Map.of("amount", index)),
                                EvaluationOptions.defaults()).requireBoolean() == (index >= 18);
                    }).toList();

            assertThat(executor.invokeAll(tasks)).allSatisfy(future -> assertThat(future.get()).isTrue());
        } finally {
            executor.shutdownNow();
        }
        assertThat(compileCalls).hasValue(1_000);
        assertThat(evaluateCalls).hasValue(1_000);
        assertThat(compileThreads).hasSizeGreaterThan(1);
        assertThat(evaluateThreads).hasSizeGreaterThan(1);
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
