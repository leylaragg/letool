package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.DependencyCoverage;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证编译产物摘要覆盖全部受控语义，并保持不可变和并发安全。
 */
@DisplayName("编译产物契约")
class CompiledExpressionContractTest {

    private static final TypeDescriptor INTEGER =
            TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private static final String OTHER_DIGEST = "f".repeat(64);

    private final ExpressionCompilationPipeline compiler = new ExpressionCompilationPipeline();
    private final FunctionRegistry functions = FunctionRegistry.builder().build();
    private final EngineLimits limits = EngineLimits.defaults();

    /** 相同源码和环境必须得到相同的产物摘要。 */
    @Test
    @DisplayName("相同输入产生稳定产物摘要")
    void sameInputProducesStableArtifactDigest() {
        FactContract contract = FactContract.builder("amount-v1")
                .path("amount", INTEGER)
                .build();

        CompiledExpression first = compile("${amount} > 100", contract);
        CompiledExpression second = compile("${amount} > 100", contract);

        assertThat(first.artifactDigest())
                .isEqualTo(second.artifactDigest())
                .matches("[0-9a-f]{64}");
        assertThat(first.factContractDigest()).isEqualTo(contract.contractDigest());
        assertThat(first.environmentDigest()).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    /** 源码或事实类型变化都必须形成不同产物。 */
    @Test
    @DisplayName("表达式和事实契约变化改变产物摘要")
    void expressionOrContractChangesArtifactDigest() {
        FactContract integer = FactContract.builder("amount-v1")
                .path("amount", INTEGER).build();
        FactContract decimal = FactContract.builder("amount-v1")
                .path("amount", TypeDescriptor.scalar(TypeKind.DECIMAL, false)).build();
        CompiledExpression baseline = compile("${amount} > 100", integer);

        assertThat(compile("${amount} >= 100", integer).artifactDigest())
                .isNotEqualTo(baseline.artifactDigest());
        assertThat(compile("${amount} > 100", decimal).artifactDigest())
                .isNotEqualTo(baseline.artifactDigest());
    }

    /** 环境摘要是编译和求值语义的单一兼容身份。 */
    @Test
    @DisplayName("执行环境变化改变产物摘要")
    void environmentChangesArtifactDigest() {
        CompiledExpression baseline = compile(
                "true", FactContract.builder("empty-v1").build());
        CompiledExpression changed = copy(
                baseline,
                baseline.factContractDigest(),
                OTHER_DIGEST,
                baseline.functionDependencies());

        assertThat(changed.artifactDigest()).isNotEqualTo(baseline.artifactDigest());
        assertThat(changed.environmentDigest()).isEqualTo(OTHER_DIGEST);
    }

    /** 事实契约摘要独立参与产物身份，不能只依赖 AST。 */
    @Test
    @DisplayName("事实契约摘要变化改变产物摘要")
    void factContractDigestChangesArtifactDigest() {
        CompiledExpression baseline = compile(
                "true", FactContract.builder("empty-v1").build());
        CompiledExpression changed = copy(
                baseline,
                OTHER_DIGEST,
                baseline.environmentDigest(),
                baseline.functionDependencies());

        assertThat(changed.artifactDigest()).isNotEqualTo(baseline.artifactDigest());
    }

    /** 函数依赖保持源码首次出现顺序且不能被调用方修改。 */
    @Test
    @DisplayName("函数依赖快照不可修改")
    void functionDependenciesAreImmutable() {
        CompiledExpression baseline = compile(
                "true", FactContract.builder("empty-v1").build());
        CompiledExpression withFunction = copy(
                baseline,
                baseline.factContractDigest(),
                baseline.environmentDigest(),
                List.of("SAFE"));

        assertThat(withFunction.functionDependencies()).containsExactly("SAFE");
        assertThatThrownBy(() -> withFunction.functionDependencies().add("OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 构造边界拒绝伪造摘要、重复函数和无效版本。 */
    @Test
    @DisplayName("产物拒绝无效语义身份")
    void invalidSemanticIdentityIsRejected() {
        CompiledExpression baseline = compile(
                "true", FactContract.builder("empty-v1").build());

        assertThatThrownBy(() -> new CompiledExpression(
                baseline.source(), baseline.ast(), baseline.resultType(),
                baseline.dependencies(), List.of(), baseline.dependencyCoverage(),
                " ", baseline.semanticVersion(),
                baseline.factContractDigest(), baseline.environmentDigest()))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> new CompiledExpression(
                baseline.source(), baseline.ast(), baseline.resultType(),
                baseline.dependencies(), List.of(), baseline.dependencyCoverage(),
                baseline.languageVersion(),
                baseline.semanticVersion(), "invalid", baseline.environmentDigest()))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> new CompiledExpression(
                baseline.source(), baseline.ast(), baseline.resultType(),
                baseline.dependencies(), List.of("SAME", "SAME"),
                baseline.dependencyCoverage(),
                baseline.languageVersion(), baseline.semanticVersion(),
                baseline.factContractDigest(), baseline.environmentDigest()))
                .isInstanceOf(RuleEngineException.class);
    }

    /** 产物展示不回显表达式源码，只展示类型和安全摘要。 */
    @Test
    @DisplayName("产物字符串不泄露规则源码")
    void toStringDoesNotExposeSource() {
        CompiledExpression expression = compile(
                "'business-secret' = 'business-secret'",
                FactContract.builder("empty-v1").build());

        assertThat(expression.toString())
                .contains(expression.artifactDigest())
                .doesNotContain("business-secret");
    }

    /** 无状态编译流水线在并发请求下仍产生唯一稳定摘要。 */
    @Test
    @DisplayName("并发编译产生稳定产物摘要")
    void concurrentCompilationProducesStableDigest() {
        FactContract contract = FactContract.builder("amount-v1")
                .path("amount", INTEGER).build();
        Set<String> digests = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 1_000).parallel()
                .mapToObj(index -> compile("${amount} > 100", contract).artifactDigest())
                .forEach(digests::add);

        assertThat(digests).hasSize(1);
    }

    /** 通过固定编译流水线获得测试产物。 */
    private CompiledExpression compile(String source, FactContract contract) {
        return compiler.compile(source, contract, functions, limits).requireCompiled();
    }

    /** 复制产物并替换测试关注的受控语义维度。 */
    private static CompiledExpression copy(
            CompiledExpression source,
            String factContractDigest,
            String environmentDigest,
            List<String> functionDependencies) {
        return new CompiledExpression(
                source.source(),
                source.ast(),
                source.resultType(),
                source.dependencies(),
                new ArrayList<>(functionDependencies),
                source.dependencyCoverage(),
                source.languageVersion(),
                source.semanticVersion(),
                factContractDigest,
                environmentDigest);
    }
}
