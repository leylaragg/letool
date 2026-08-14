package com.github.leyland.letool.ruleengine.autoconfigure;

import com.github.leyland.letool.exception.config.ExceptionAutoConfiguration;
import com.github.leyland.letool.exception.message.MessageBundleContributor;
import com.github.leyland.letool.exception.message.DefaultMessageResolver;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.ruleengine.api.ExpressionEngine;
import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.CompilationResult;
import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.compile.ExpressionCompiler;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticMessageResolver;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
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
import com.github.leyland.letool.ruleengine.function.FunctionDescriptor;
import com.github.leyland.letool.ruleengine.function.FunctionDeterminism;
import com.github.leyland.letool.ruleengine.function.FunctionEffect;
import com.github.leyland.letool.ruleengine.function.FunctionSignature;
import com.github.leyland.letool.ruleengine.function.FunctionThreading;
import com.github.leyland.letool.ruleengine.function.RuleFunction;
import com.github.leyland.letool.ruleengine.function.RuleFunctionFactory;
import com.github.leyland.letool.ruleengine.type.FactContract;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 规则引擎 Spring Boot 自动配置契约测试。 */
class RuleEngineAutoConfigurationTest {

    private static final FactContract EMPTY_CONTRACT = FactContract.builder("empty-v1").build();
    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ExceptionAutoConfiguration.class,
                    RuleEngineAutoConfiguration.class));

    /** 默认上下文提供完整且唯一的规则引擎协作者。 */
    @Test
    void defaultContextProvidesSingleRuleEngineBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ExpressionCompiler.class);
            assertThat(context.getBean(ExpressionCompiler.class))
                    .isInstanceOf(DefaultExpressionCompiler.class);
            assertThat(context).hasSingleBean(ExpressionEvaluator.class);
            assertThat(context.getBean(ExpressionEvaluator.class))
                    .isInstanceOf(DefaultExpressionEvaluator.class);
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
            assertThat(context).hasSingleBean(RuleEngineProperties.class);
        });
    }

    /** 规则资源贡献者始终保留自己的 Bean 名，且与通用资源并存。 */
    @Test
    void ruleMessageBundleCoexistsWithCommonContributor() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("ruleEngineMessageBundle");
            assertThat(context.getBeansOfType(MessageBundleContributor.class)).hasSize(2);
            MessageBundleContributor contributor =
                    context.getBean("ruleEngineMessageBundle", MessageBundleContributor.class);
            assertThat(contributor.getBasenames())
                    .containsExactly("i18n/letool-rule-engine/messages");
        });
    }

    /** 总开关关闭时不注册规则引擎 Bean、属性和资源。 */
    @Test
    void disabledPropertyBacksOffEntireAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.rule-engine.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExpressionCompiler.class);
                    assertThat(context).doesNotHaveBean(ExpressionEvaluator.class);
                    assertThat(context).doesNotHaveBean(ExpressionEngine.class);
                    assertThat(context).doesNotHaveBean(DiagnosticMessageResolver.class);
                    assertThat(context).doesNotHaveBean(RuleEngineProperties.class);
                    assertThat(context).doesNotHaveBean("ruleEngineMessageBundle");
                });
    }

    /** 用户编译器只替换默认编译器。 */
    @Test
    void customCompilerOnlyReplacesCompiler() {
        contextRunner.withUserConfiguration(CustomCompilerConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ExpressionCompiler.class);
            assertThat(context.getBean(ExpressionCompiler.class))
                    .isSameAs(context.getBean("customCompiler"));
            assertThat(context.getBean(ExpressionEvaluator.class))
                    .isInstanceOf(DefaultExpressionEvaluator.class);
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
        });
    }

    /** 用户求值器只替换默认求值器。 */
    @Test
    void customEvaluatorOnlyReplacesEvaluator() {
        contextRunner.withUserConfiguration(CustomEvaluatorConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ExpressionEvaluator.class);
            assertThat(context.getBean(ExpressionEvaluator.class))
                    .isSameAs(context.getBean("customEvaluator"));
            assertThat(context.getBean(ExpressionCompiler.class))
                    .isInstanceOf(DefaultExpressionCompiler.class);
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
        });
    }

    /** 用户引擎退让时仍提供其他默认协作者。 */
    @Test
    void customEngineOnlyReplacesEngine() {
        contextRunner.withUserConfiguration(CustomEngineConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context.getBean(ExpressionEngine.class))
                    .isSameAs(context.getBean("customEngine"));
            assertThat(context.getBean(ExpressionCompiler.class))
                    .isInstanceOf(DefaultExpressionCompiler.class);
            assertThat(context.getBean(ExpressionEvaluator.class))
                    .isInstanceOf(DefaultExpressionEvaluator.class);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
        });
    }

    /** 用户诊断解析器只替换默认诊断解析器。 */
    @Test
    void customDiagnosticResolverOnlyReplacesDiagnosticResolver() {
        contextRunner.withUserConfiguration(CustomDiagnosticConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
            assertThat(context.getBean(DiagnosticMessageResolver.class))
                    .isSameAs(context.getBean("customDiagnosticResolver"));
            assertThat(context.getBean(ExpressionCompiler.class))
                    .isInstanceOf(DefaultExpressionCompiler.class);
            assertThat(context.getBean(ExpressionEvaluator.class))
                    .isInstanceOf(DefaultExpressionEvaluator.class);
            assertThat(context).hasSingleBean(ExpressionEngine.class);
        });
    }

    /** 多个无主候选编译器由 Spring 报告标准歧义。 */
    @Test
    void multipleCompilersWithoutPrimaryFailStartup() {
        contextRunner.withUserConfiguration(AmbiguousCompilerConfiguration.class).run(context ->
                assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    /** 多个无主候选求值器由 Spring 报告标准歧义。 */
    @Test
    void multipleEvaluatorsWithoutPrimaryFailStartup() {
        contextRunner.withUserConfiguration(AmbiguousEvaluatorConfiguration.class).run(context ->
                assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    /** 关闭异常国际化时仍通过默认消息解析器接入适配器。 */
    @Test
    void disabledExceptionI18nUsesDefaultResolverAndAdapter() {
        contextRunner
                .withPropertyValues("letool.exception.i18n.enabled=false")
                .run(context -> {
                    assertThat(context.getBean(MessageResolver.class))
                            .isInstanceOf(DefaultMessageResolver.class);
                    assertThat(context.getBean(DiagnosticMessageResolver.class))
                            .isInstanceOf(MessageResolverDiagnosticAdapter.class);
                });
    }

    /** 关闭异常模块时规则引擎独立启动并使用中文回退。 */
    @Test
    void disabledExceptionModuleUsesChineseDiagnosticFallback() {
        contextRunner
                .withPropertyValues("letool.exception.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessageResolver.class);
                    assertThat(context).hasSingleBean(ExpressionEngine.class);
                    assertThat(context.getBean(DiagnosticMessageResolver.class)
                            .resolve(sampleDiagnostic(), Locale.ENGLISH))
                            .contains("未知字符");
                });
    }

    /** 应用 messageSource 的英文文案覆盖 Starter 资源。 */
    @Test
    void applicationMessageSourceOverridesEnglishRuleMessage() {
        contextRunner.withUserConfiguration(ApplicationMessageSourceConfiguration.class)
                .run(context -> assertThat(context.getBean(DiagnosticMessageResolver.class)
                        .resolve(sampleDiagnostic(), Locale.ENGLISH))
                        .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] application override"));
    }

    /** 自动配置导入资源只声明一个精确类名。 */
    @Test
    void autoConfigurationImportsContainsExactSingleLine() throws IOException {
        String resource = "META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList())
                    .containsExactly(RuleEngineAutoConfiguration.class.getName());
        }
    }

    /** 自动配置生产类不得引用数据库 API 或数据库配置类型。 */
    @Test
    void productionAutoConfigurationHasNoDatabaseReferences() throws IOException {
        List<Class<?>> productionTypes = List.of(
                RuleEngineAutoConfiguration.class,
                RuleEngineProperties.class,
                MessageResolverDiagnosticAdapter.class);
        for (Class<?> type : productionTypes) {
            String resource = type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                String classFile = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertThat(classFile).as(resource)
                        .doesNotContain("java/sql", "javax/sql", "jakarta/persistence",
                                "DataSource", "JdbcTemplate", "EntityManager");
            }
        }
    }

    /** Spring 声明的共享函数和调用级工厂都参与真实编译与求值。 */
    @Test
    void functionInstanceAndFactoryAreCollectedForRealEvaluation() {
        contextRunner.withUserConfiguration(FunctionConfiguration.class).run(context -> {
            ExpressionEngine engine = context.getBean(ExpressionEngine.class);
            CompiledExpression expression = engine.compile(
                    "$ONE() + $TWO()", EMPTY_CONTRACT).requireCompiled();

            assertThat(engine.evaluate(
                    expression, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults())
                    .requireValue().toSafeJavaValue()).isEqualTo(BigInteger.valueOf(3));
        });
    }

    /** Bean 声明顺序变化时，Spring 排序收集和 core 编码排序仍得到相同指纹。 */
    @Test
    void orderedFunctionCollectionKeepsCanonicalFingerprintStable() {
        AtomicReference<String> firstFingerprint = new AtomicReference<>();
        contextRunner.withUserConfiguration(OrderedFunctionsConfiguration.class).run(context ->
                firstFingerprint.set(context.getBean(ExpressionEngine.class)
                        .compile("$ALPHA() + $BETA()", EMPTY_CONTRACT)
                        .requireCompiled().functionCatalogFingerprint()));

        contextRunner.withUserConfiguration(ReverseDeclaredFunctionsConfiguration.class)
                .run(context -> assertThat(context.getBean(ExpressionEngine.class)
                        .compile("$ALPHA() + $BETA()", EMPTY_CONTRACT)
                        .requireCompiled().functionCatalogFingerprint())
                        .isEqualTo(firstFingerprint.get()));
    }

    /** 规范化后重复的函数编码应直接阻止上下文启动。 */
    @Test
    void duplicateCanonicalFunctionCodesFailStartup() {
        contextRunner.withUserConfiguration(DuplicateFunctionConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            RuleEngineException root = rootRuleEngineException(failure);
            assertThat(root.getErrorCode()).isSameAs(RuleEngineErrorCode.REGISTRATION_CONFLICT);
        });
    }

    /** 非线程安全的直接函数应在构建引擎时被拒绝。 */
    @Test
    void nonThreadSafeDirectFunctionFailsStartup() {
        contextRunner.withUserConfiguration(UnsafeFunctionConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            assertThat(rootRuleEngineException(failure).getErrorCode())
                    .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
        });
    }

    /** 工厂描述符异常应被净化为可辨别且不泄密的启动失败。 */
    @Test
    void hostileFactoryDescriptorFailsStartupWithoutLeakingSecret() {
        contextRunner.withUserConfiguration(HostileFactoryConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            RuleEngineException root = rootRuleEngineException(failure);
            assertThat(root.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
            assertThat(root.getMessage()).doesNotContain("factory-secret");
            assertThat(root.getCause()).isNull();
        });
    }

    /** 九项绑定值都由自动配置传给引擎编译器 SPI。 */
    @Test
    void allNineConfiguredLimitsReachEngineCompiler() {
        contextRunner
                .withUserConfiguration(CapturingCompilerConfiguration.class)
                .withPropertyValues(
                        "letool.rule-engine.limits.max-source-length=101",
                        "letool.rule-engine.limits.max-tokens=102",
                        "letool.rule-engine.limits.max-ast-depth=103",
                        "letool.rule-engine.limits.max-function-calls=104",
                        "letool.rule-engine.limits.max-trace-nodes=105",
                        "letool.rule-engine.limits.max-summary-length=106",
                        "letool.rule-engine.limits.max-fact-depth=107",
                        "letool.rule-engine.limits.max-fact-nodes=108",
                        "letool.rule-engine.limits.max-container-size=109")
                .run(context -> {
                    context.getBean(ExpressionEngine.class).compile("true", EMPTY_CONTRACT);
                    EngineLimits limits = context.getBean(
                            CapturingCompilerConfiguration.class).captured.get();
                    assertThat(List.of(
                            limits.getMaxSourceLength(), limits.getMaxTokens(),
                            limits.getMaxAstDepth(), limits.getMaxFunctionCalls(),
                            limits.getMaxTraceNodes(), limits.getMaxSummaryLength(),
                            limits.getMaxFactDepth(), limits.getMaxFactNodes(),
                            limits.getMaxContainerSize()))
                            .containsExactly(101, 102, 103, 104, 105, 106, 107, 108, 109);
                });
    }

    /** 源码、Token 与 AST 配置分别约束真实编译路径。 */
    @Test
    void configuredCompilationBudgetsAreEnforced() {
        assertCompilationLimit(
                "letool.rule-engine.limits.max-source-length=4",
                "false",
                RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED);
        assertCompilationLimit(
                "letool.rule-engine.limits.max-tokens=2",
                "true = true",
                RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED);
        assertCompilationLimit(
                "letool.rule-engine.limits.max-ast-depth=1",
                "NOT true",
                RuleDiagnosticCode.AST_DEPTH_EXCEEDED);
    }

    /** 事实深度、节点数与容器大小配置约束真实事实构建。 */
    @Test
    void configuredFactBudgetsAreEnforced() {
        contextRunner
                .withPropertyValues(
                        "letool.rule-engine.limits.max-fact-depth=2",
                        "letool.rule-engine.limits.max-fact-nodes=2",
                        "letool.rule-engine.limits.max-container-size=1")
                .run(context -> {
                    EngineLimits limits = context.getBean(RuleEngineProperties.class)
                            .getLimits().toEngineLimits();
                    assertThatThrownBy(() -> RuleFacts.fromMap(
                            Map.of("outer", Map.of("inner", 1)), limits))
                            .isInstanceOf(RuleEngineException.class);
                    assertThatThrownBy(() -> RuleFacts.fromMap(
                            Map.of("first", 1, "second", 2), limits))
                            .isInstanceOf(RuleEngineException.class);
                });
    }

    /** 引擎配置比请求更严格时，请求不能放宽函数调用上限。 */
    @Test
    void engineFunctionCallLimitCannotBeRelaxedByRequest() {
        contextRunner.withUserConfiguration(FunctionConfiguration.class)
                .withPropertyValues("letool.rule-engine.limits.max-function-calls=1")
                .run(context -> {
                    ExpressionEngine engine = context.getBean(ExpressionEngine.class);
                    CompiledExpression expression = engine.compile(
                            "$ONE() + $ONE()", EMPTY_CONTRACT).requireCompiled();
                    ExpressionEvaluationResult result = engine.evaluate(
                            expression,
                            RuleFacts.fromMap(Map.of()),
                            EvaluationOptions.of(
                                    Locale.ROOT, ZoneId.of("UTC"), false,
                                    limitsWithFunctionCalls(1000)));

                    assertThat(result.isSuccessful()).isFalse();
                    assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                            .containsExactly(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED);
                });
    }

    /** 请求可以把较宽的引擎函数调用上限进一步收紧。 */
    @Test
    void requestCanTightenEngineFunctionCallLimit() {
        contextRunner.withUserConfiguration(FunctionConfiguration.class)
                .withPropertyValues("letool.rule-engine.limits.max-function-calls=1000")
                .run(context -> {
                    ExpressionEngine engine = context.getBean(ExpressionEngine.class);
                    CompiledExpression expression = engine.compile(
                            "$ONE() + $ONE()", EMPTY_CONTRACT).requireCompiled();
                    ExpressionEvaluationResult result = engine.evaluate(
                            expression,
                            RuleFacts.fromMap(Map.of()),
                            EvaluationOptions.of(
                                    Locale.ROOT, ZoneId.of("UTC"), false,
                                    limitsWithFunctionCalls(1)));

                    assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                            .containsExactly(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED);
                });
    }

    /** 轨迹节点数与摘要长度配置约束真实求值轨迹。 */
    @Test
    void configuredTraceAndSummaryBudgetsAreEnforced() {
        contextRunner
                .withPropertyValues(
                        "letool.rule-engine.limits.max-trace-nodes=1",
                        "letool.rule-engine.limits.max-summary-length=3")
                .run(context -> {
                    ExpressionEngine engine = context.getBean(ExpressionEngine.class);
                    CompiledExpression expression = engine.compile(
                            "'abcdef' = 'abcdef'", EMPTY_CONTRACT).requireCompiled();
                    ExpressionEvaluationResult result = engine.evaluate(
                            expression,
                            RuleFacts.fromMap(Map.of()),
                            EvaluationOptions.of(
                                    Locale.ROOT, ZoneId.of("UTC"), true,
                                    EngineLimits.defaults()));

                    assertThat(result.requireBoolean()).isTrue();
                    assertThat(result.trace().nodes()).hasSize(1)
                            .allSatisfy(node -> assertThat(node.summary()).hasSizeLessThanOrEqualTo(3));
                    assertThat(result.trace().isTruncated()).isTrue();
                });
    }

    /** 同一 Spring 默认引擎并发编译求值一千次时结果与指纹一致。 */
    @Test
    void sharedDefaultEngineIsStableAcrossOneThousandConcurrentCalls() throws Exception {
        contextRunner.run(context -> assertConcurrentEngine(
                context.getBean(ExpressionEngine.class), null, null));
    }

    /** 自定义线程安全编译器和求值器的注入路径并发一千次不串状态。 */
    @Test
    void sharedCustomSpisAreStableAcrossOneThousandConcurrentCalls() throws Exception {
        contextRunner.withUserConfiguration(ConcurrentSpiConfiguration.class).run(context -> {
            ConcurrentSpiConfiguration configuration =
                    context.getBean(ConcurrentSpiConfiguration.class);
            assertConcurrentEngine(
                    context.getBean(ExpressionEngine.class),
                    configuration.compileCalls,
                    configuration.evaluateCalls);
        });
    }

    private static RuleDiagnostic sampleDiagnostic() {
        return new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL,
                0,
                1,
                List.of(),
                null);
    }

    private void assertCompilationLimit(
            String property, String source, RuleDiagnosticCode expectedCode) {
        contextRunner.withPropertyValues(property).run(context -> {
            CompilationResult<CompiledExpression> result = context
                    .getBean(ExpressionEngine.class).compile(source, EMPTY_CONTRACT);
            assertThat(result.diagnostics()).extracting(RuleDiagnostic::code)
                    .containsExactly(expectedCode);
        });
    }

    private static EngineLimits limitsWithFunctionCalls(int maximum) {
        EngineLimits defaults = EngineLimits.defaults();
        return new EngineLimits(
                defaults.getMaxSourceLength(), defaults.getMaxTokens(),
                defaults.getMaxAstDepth(), maximum, defaults.getMaxTraceNodes(),
                defaults.getMaxSummaryLength(), defaults.getMaxFactDepth(),
                defaults.getMaxFactNodes(), defaults.getMaxContainerSize());
    }

    private static RuleEngineException rootRuleEngineException(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return (RuleEngineException) current;
    }

    private static void assertConcurrentEngine(
            ExpressionEngine engine,
            AtomicInteger compileCalls,
            AtomicInteger evaluateCalls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Callable<ConcurrentResult>> tasks = IntStream.range(0, 1000)
                .mapToObj(index -> (Callable<ConcurrentResult>) () -> {
                    CompiledExpression expression = engine.compile(
                            "1 + 2", EMPTY_CONTRACT).requireCompiled();
                    Object value = engine.evaluate(
                            expression,
                            RuleFacts.fromMap(Map.of()),
                            EvaluationOptions.defaults()).requireValue().toSafeJavaValue();
                    return new ConcurrentResult(
                            expression.functionCatalogFingerprint(), value);
                }).toList();
        try {
            List<Future<ConcurrentResult>> futures = executor.invokeAll(tasks);
            List<ConcurrentResult> results = new ArrayList<>(futures.size());
            for (Future<ConcurrentResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(results).hasSize(1000).allSatisfy(result ->
                    assertThat(result.value()).isEqualTo(BigInteger.valueOf(3)));
            assertThat(results).extracting(ConcurrentResult::fingerprint)
                    .containsOnly(results.get(0).fingerprint());
            if (compileCalls != null) assertThat(compileCalls).hasValue(1000);
            if (evaluateCalls != null) assertThat(evaluateCalls).hasValue(1000);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static ExpressionCompiler delegateCompiler() {
        return (source, contract, registry, limits) ->
                new DefaultExpressionCompiler().compile(source, contract, registry, limits);
    }

    private static ExpressionEvaluator delegateEvaluator() {
        return (expression, facts, registry, options) ->
                new DefaultExpressionEvaluator().evaluate(expression, facts, registry, options);
    }

    private static ExpressionEngine stubEngine() {
        return new ExpressionEngine() {
            @Override
            public CompilationResult<CompiledExpression> compile(
                    String source, FactContract factContract) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExpressionEvaluationResult evaluate(
                    CompiledExpression expression, RuleFacts facts, EvaluationOptions options) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCompilerConfiguration {
        @Bean ExpressionCompiler customCompiler() { return delegateCompiler(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEvaluatorConfiguration {
        @Bean ExpressionEvaluator customEvaluator() { return delegateEvaluator(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEngineConfiguration {
        @Bean ExpressionEngine customEngine() { return stubEngine(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDiagnosticConfiguration {
        @Bean DiagnosticMessageResolver customDiagnosticResolver() {
            return (diagnostic, locale) -> "custom";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousCompilerConfiguration {
        @Bean ExpressionCompiler firstCompiler() { return delegateCompiler(); }
        @Bean ExpressionCompiler secondCompiler() { return delegateCompiler(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousEvaluatorConfiguration {
        @Bean ExpressionEvaluator firstEvaluator() { return delegateEvaluator(); }
        @Bean ExpressionEvaluator secondEvaluator() { return delegateEvaluator(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationMessageSourceConfiguration {
        @Bean(name = "messageSource")
        MessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();
            source.addMessage(
                    RuleDiagnosticCode.UNKNOWN_CHARACTER.getCode(),
                    Locale.ENGLISH,
                    "application override");
            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FunctionConfiguration {
        @Bean RuleFunction oneFunction() { return new ConstantFunction("ONE", 1); }
        @Bean RuleFunctionFactory twoFunctionFactory() {
            return factory(new ConstantFunction(
                    "TWO", 2, FunctionThreading.INVOCATION_SCOPED));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedFunctionsConfiguration {
        @Bean @Order(20) RuleFunction betaFunction() {
            return new ConstantFunction("BETA", 2);
        }
        @Bean @Order(10) RuleFunction alphaFunction() {
            return new ConstantFunction("ALPHA", 1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReverseDeclaredFunctionsConfiguration {
        @Bean @Order(10) RuleFunction alphaFunction() {
            return new ConstantFunction("ALPHA", 1);
        }
        @Bean @Order(20) RuleFunction betaFunction() {
            return new ConstantFunction("BETA", 2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateFunctionConfiguration {
        @Bean RuleFunction lowerFunction() { return new ConstantFunction("same", 1); }
        @Bean RuleFunction upperFunction() { return new ConstantFunction("SAME", 2); }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnsafeFunctionConfiguration {
        @Bean RuleFunction unsafeFunction() {
            return new ConstantFunction("UNSAFE", 1, FunctionThreading.INVOCATION_SCOPED);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostileFactoryConfiguration {
        @Bean RuleFunctionFactory hostileFactory() {
            return new RuleFunctionFactory() {
                @Override public FunctionDescriptor descriptor() {
                    throw new IllegalStateException("factory-secret");
                }
                @Override public RuleFunction create() {
                    return new ConstantFunction("UNREACHABLE", 1);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CapturingCompilerConfiguration {
        private final AtomicReference<EngineLimits> captured = new AtomicReference<>();

        @Bean
        ExpressionCompiler capturingCompiler() {
            return (source, contract, registry, limits) -> {
                captured.set(limits);
                return new DefaultExpressionCompiler().compile(
                        source, contract, registry, limits);
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConcurrentSpiConfiguration {
        private final AtomicInteger compileCalls = new AtomicInteger();
        private final AtomicInteger evaluateCalls = new AtomicInteger();

        @Bean
        ExpressionCompiler concurrentCompiler() {
            DefaultExpressionCompiler delegate = new DefaultExpressionCompiler();
            return (source, contract, registry, limits) -> {
                compileCalls.incrementAndGet();
                return delegate.compile(source, contract, registry, limits);
            };
        }

        @Bean
        ExpressionEvaluator concurrentEvaluator() {
            DefaultExpressionEvaluator delegate = new DefaultExpressionEvaluator();
            return (expression, facts, registry, options) -> {
                evaluateCalls.incrementAndGet();
                return delegate.evaluate(expression, facts, registry, options);
            };
        }
    }

    private static RuleFunctionFactory factory(RuleFunction prototype) {
        FunctionDescriptor descriptor = FunctionDescriptor.from(prototype);
        return new RuleFunctionFactory() {
            @Override public FunctionDescriptor descriptor() { return descriptor; }
            @Override public RuleFunction create() {
                return new ConstantFunction(
                        descriptor.code(), 2, FunctionThreading.INVOCATION_SCOPED);
            }
        };
    }

    private static final class ConstantFunction implements RuleFunction {
        private final String code;
        private final long value;
        private final FunctionThreading threading;

        private ConstantFunction(String code, long value) {
            this(code, value, FunctionThreading.THREAD_SAFE);
        }

        private ConstantFunction(String code, long value, FunctionThreading threading) {
            this.code = code;
            this.value = value;
            this.threading = threading;
        }

        @Override public String code() { return code; }
        @Override public String semanticVersion() { return "1"; }
        @Override public FunctionSignature signature() { return FunctionSignature.empty(); }
        @Override public TypeDescriptor returnType() { return INTEGER; }
        @Override public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(
                    FunctionDeterminism.DETERMINISTIC, FunctionEffect.PURE, threading);
        }
        @Override public FactValue execute(
                FunctionArguments arguments, FunctionContext context) {
            return FactValues.integer(value);
        }
    }

    private record ConcurrentResult(String fingerprint, Object value) {
    }
}
