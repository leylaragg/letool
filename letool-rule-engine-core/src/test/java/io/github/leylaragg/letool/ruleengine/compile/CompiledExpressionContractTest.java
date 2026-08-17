package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeCompatibility;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.AbstractList;
import java.util.Iterator;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CompiledExpressionContractTest {

    private final ExpressionCompiler compiler = new DefaultExpressionCompiler();

    @Test
    @DisplayName("相同输入和目录应产生稳定且完整的编译指纹")
    void shouldProduceStableFingerprint() {
        FactContract contract = contract("v1", TypeKind.DECIMAL);
        CompiledExpression first = compile("${amount} > 100", contract);
        CompiledExpression second = compile("${amount} > 100", contract);

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint()).hasSize(64);
        assertThat(first.fingerprint()).isEqualTo(
                "2da0e646093e5f6c80b0f9f654dfef65f7ccce4dac1a94b889a621114f3c25c9");
        assertThat(first.languageVersion()).isEqualTo(DefaultExpressionCompiler.LANGUAGE_VERSION);
        assertThat(first.engineVersion()).isEqualTo(DefaultExpressionCompiler.ENGINE_VERSION);
        assertThat(first.typeCatalogFingerprint()).isEqualTo(TypeCompatibility.TYPE_CATALOG_FINGERPRINT);
        assertThat(first.factContractFingerprint()).isEqualTo(contract.fingerprint());
        assertThat(first.functionCatalogFingerprint())
                .isEqualTo(FunctionRegistry.builder().build().fingerprint());
    }

    @Test
    @DisplayName("源码、事实契约和依赖范围变化都应改变编译指纹")
    void shouldCoverSemanticFingerprintDimensions() {
        FactContract decimal = contract("v1", TypeKind.DECIMAL);
        FactContract integer = contract("v2", TypeKind.INTEGER);

        assertThat(compile("${amount} > 100", decimal).fingerprint())
                .isNotEqualTo(compile("${amount} >= 100", decimal).fingerprint())
                .isNotEqualTo(compile("${amount} > 100", integer).fingerprint());
    }

    @Test
    @DisplayName("版本、目录、结果类型、依赖和函数依赖均应参与编译指纹")
    void shouldCoverEveryArtifactFingerprintDimension() {
        CompiledExpression base = compile("${amount} > 100", contract("v1", TypeKind.DECIMAL));

        assertThat(copy(base, TypeDescriptor.scalar(TypeKind.INTEGER, false),
                base.dependencies(), base.functionDependencies(), base.languageVersion(),
                base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), List.of("SAFE"),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), "0".repeat(64), "2.0",
                "1".repeat(64), "2".repeat(64)).fingerprint())
                .isNotEqualTo(base.fingerprint());

        ExpressionDependency original = base.dependencies().values().get(0);
        ExpressionDependencies pathChanged = ExpressionDependencies.of(List.of(
                new ExpressionDependency(io.github.leylaragg.letool.ruleengine.fact.FactPathParser.parse("other"),
                        original.expectedType(), original.startPosition(), original.endPosition())));
        ExpressionDependencies typeChanged = ExpressionDependencies.of(List.of(
                new ExpressionDependency(original.path(),
                        TypeDescriptor.scalar(TypeKind.DECIMAL, false),
                        original.startPosition(), original.endPosition())));
        ExpressionDependencies rangeChanged = ExpressionDependencies.of(List.of(
                new ExpressionDependency(original.path(), original.expectedType(), 1, 9)));
        assertThat(copy(base, base.resultType(), pathChanged, base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), typeChanged, base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), rangeChanged, base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
    }

    @Test
    @DisplayName("每个版本和目录维度单独变化都应改变编译指纹")
    void shouldFingerprintEachVersionDimensionIndependently() {
        CompiledExpression base = compile("${amount} > 100", contract("v1", TypeKind.DECIMAL));

        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                "1.1", base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), "0".repeat(64), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), "2.0",
                base.factContractFingerprint(), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                "1".repeat(64), base.functionCatalogFingerprint()).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(copy(base, base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), "2".repeat(64)).fingerprint())
                .isNotEqualTo(base.fingerprint());
    }

    @Test
    @DisplayName("类型化依赖和函数依赖顺序应参与编译指纹")
    void shouldIncludeDependencyOrderInFingerprint() {
        CompiledExpression base = compile("${amount} > 100", contract("v1", TypeKind.DECIMAL));
        ExpressionDependency amount = base.dependencies().values().get(0);
        ExpressionDependency other = new ExpressionDependency(
                io.github.leylaragg.letool.ruleengine.fact.FactPathParser.parse("other"),
                amount.expectedType(), 10, 15);
        CompiledExpression first = copy(base, base.resultType(),
                ExpressionDependencies.of(List.of(amount, other)), List.of("A", "B"),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint());
        CompiledExpression second = copy(base, base.resultType(),
                ExpressionDependencies.of(List.of(other, amount)), List.of("B", "A"),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint());

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("相同源码但AST语义字段变化应改变编译指纹")
    void shouldIncludeCanonicalAstSemanticsInFingerprint() {
        CompiledExpression base = compile("${amount} > 100", contract("v1", TypeKind.DECIMAL));
        CompiledExpression changed = new CompiledExpression(base.source(),
                new LiteralNode(TokenType.INTEGER, "101", 0, base.source().length()),
                base.resultType(), base.dependencies(), base.functionDependencies(),
                base.languageVersion(), base.typeCatalogFingerprint(), base.engineVersion(),
                base.factContractFingerprint(), base.functionCatalogFingerprint());

        assertThat(changed.fingerprint()).isNotEqualTo(base.fingerprint());
    }

    @Test
    @DisplayName("CompilationResult应允许警告并拒绝成功结果中的错误")
    void shouldValidateSuccessAndMixedFailureDiagnostics() {
        CompiledExpression compiled = compile("${amount} > 100", contract("v1", TypeKind.DECIMAL));
        RuleDiagnostic warning = new RuleDiagnostic(RuleDiagnosticCode.UNKNOWN_FACT_PATH,
                DiagnosticSeverity.WARNING, DiagnosticPhase.SEMANTIC, 0, 1, List.of(), null);
        RuleDiagnostic error = new RuleDiagnostic(RuleDiagnosticCode.UNKNOWN_FACT_PATH,
                DiagnosticSeverity.ERROR, DiagnosticPhase.SEMANTIC, 0, 1, List.of(), null);

        List<RuleDiagnostic> mutable = new ArrayList<>(List.of(warning));
        CompilationResult<CompiledExpression> success = CompilationResult.success(compiled, mutable);
        mutable.clear();
        assertThat(success.isSuccessful()).isTrue();
        assertThat(success.requireCompiled()).isSameAs(compiled);
        assertThat(success.diagnostics()).containsExactly(warning);
        assertThatThrownBy(() -> success.diagnostics().add(warning))
                .isInstanceOf(UnsupportedOperationException.class);
        CompilationResult<CompiledExpression> failure =
                CompilationResult.failure(List.of(warning, error));
        assertThat(failure.isSuccessful()).isFalse();
        assertThat(failure.diagnostics()).containsExactly(warning, error);
        assertThatThrownBy(failure::requireCompiled).satisfies(throwable -> {
            assertThat(throwable).isInstanceOf(RuleEngineException.class);
            RuleEngineException exception = (RuleEngineException) throwable;
            assertThat(exception.getErrorCode().getCode()).isEqualTo("RULE_ENGINE_API_002");
            assertThat(exception.getCause()).isNull();
        });
        assertThatThrownBy(() -> CompilationResult.success(compiled, List.of(error)))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> CompilationResult.failure(List.of(warning)))
                .isInstanceOf(RuleEngineException.class);
    }

    @Test
    @DisplayName("编译产物文本表示不得回显原始源码")
    void shouldNotLeakSourceThroughToString() {
        String sensitive = "${amount} > 987654321";
        CompiledExpression compiled = compile(sensitive, contract("v1", TypeKind.DECIMAL));

        assertThat(compiled.toString()).doesNotContain(sensitive).doesNotContain("987654321");
    }

    @Test
    @DisplayName("依赖应按首次出现顺序去重并保留首次范围")
    void shouldDeduplicateDependenciesInFirstOccurrenceOrder() {
        CompiledExpression compiled = compile(
                "${amount} > 1 AND ${amount} < 9", contract("v1", TypeKind.DECIMAL));

        assertThat(compiled.dependencies().values()).hasSize(1);
        ExpressionDependency dependency = compiled.dependencies().values().get(0);
        assertThat(dependency.path().toString()).isEqualTo("amount");
        assertThat(dependency.startPosition()).isZero();
        assertThat(dependency.endPosition()).isEqualTo(9);
    }

    @Test
    @DisplayName("依赖输入读取应有独立二千零四十八项硬上限")
    void shouldBoundDependencyInputIteration() {
        ExpressionDependency dependency = compile("${amount} > 1",
                contract("v1", TypeKind.DECIMAL)).dependencies().values().get(0);
        assertThat(ExpressionDependencies.of(java.util.Collections.nCopies(2_048, dependency))
                .values()).containsExactly(dependency);
        assertThatThrownBy(() -> ExpressionDependencies.of(
                java.util.Collections.nCopies(2_049, dependency)))
                .isInstanceOf(RuleEngineException.class).hasCause(null);

        AtomicInteger nextCalls = new AtomicInteger();
        List<ExpressionDependency> infinite = new AbstractList<>() {
            @Override public ExpressionDependency get(int index) { return dependency; }
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public Iterator<ExpressionDependency> iterator() {
                return new Iterator<>() {
                    @Override public boolean hasNext() { return true; }
                    @Override public ExpressionDependency next() {
                        nextCalls.incrementAndGet();
                        return dependency;
                    }
                };
            }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThatThrownBy(() -> ExpressionDependencies.of(infinite))
                        .isInstanceOf(RuleEngineException.class));
        assertThat(nextCalls).hasValueLessThanOrEqualTo(2_049);

        List<ExpressionDependency> malicious = new AbstractList<>() {
            @Override public ExpressionDependency get(int index) {
                throw new IllegalStateException("secret-value");
            }
            @Override public int size() { return 1; }
            @Override public Iterator<ExpressionDependency> iterator() {
                return new Iterator<>() {
                    @Override public boolean hasNext() { return true; }
                    @Override public ExpressionDependency next() {
                        throw new IllegalStateException("secret-value");
                    }
                };
            }
        };
        assertThatThrownBy(() -> ExpressionDependencies.of(malicious)).satisfies(throwable -> {
            assertThat(throwable).isInstanceOf(RuleEngineException.class);
            assertThat(throwable.getCause()).isNull();
            assertThat(throwable.getMessage()).doesNotContain("secret-value");
        });
    }

    @Test
    @DisplayName("失败结果应要求至少一个错误且不得返回编译产物")
    void shouldEnforceCompilationResultContract() {
        List<RuleDiagnostic> mutable = new ArrayList<>();
        mutable.add(new RuleDiagnostic(RuleDiagnosticCode.UNKNOWN_FACT_PATH,
                DiagnosticSeverity.ERROR, DiagnosticPhase.SEMANTIC,
                0, 4, List.of("safe"), null));
        CompilationResult<CompiledExpression> failure = CompilationResult.failure(mutable);
        mutable.clear();

        assertThat(failure.diagnostics()).hasSize(1);
        assertThatThrownBy(failure::requireCompiled)
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> CompilationResult.failure(List.of()))
                .isInstanceOf(RuleEngineException.class);
    }

    @Test
    @DisplayName("同一编译器并发编译一千次应产生唯一指纹")
    void shouldCompileConcurrentlyWithStableFingerprint() {
        FactContract contract = contract("v1", TypeKind.DECIMAL);
        Set<String> fingerprints = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 1_000).parallel()
                .mapToObj(index -> compile("${amount} > 100", contract).fingerprint())
                .forEach(fingerprints::add);

        assertThat(fingerprints).hasSize(1);
    }

    private CompiledExpression compile(String source, FactContract contract) {
        return compiler.compile(source, contract, FunctionRegistry.builder().build(),
                EngineLimits.defaults()).requireCompiled();
    }

    private static FactContract contract(String version, TypeKind typeKind) {
        return FactContract.builder(version)
                .path("amount", TypeDescriptor.scalar(typeKind, true))
                .build();
    }

    private static CompiledExpression copy(CompiledExpression source,
            TypeDescriptor resultType, ExpressionDependencies dependencies,
            List<String> functions, String languageVersion, String typeFingerprint,
            String engineVersion, String factFingerprint, String functionFingerprint) {
        return new CompiledExpression(source.source(), source.ast(), resultType,
                dependencies, functions, languageVersion, typeFingerprint,
                engineVersion, factFingerprint, functionFingerprint);
    }
}
