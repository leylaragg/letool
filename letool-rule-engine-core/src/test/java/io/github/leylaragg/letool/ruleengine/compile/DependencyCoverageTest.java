package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionFactAccess;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证编译产物明确报告静态事实依赖是否已经完整。
 */
@DisplayName("规则事实依赖覆盖状态")
class DependencyCoverageTest {

    /** 仅读取显式参数的函数不会破坏静态依赖完整性。 */
    @Test
    @DisplayName("显式参数函数保持完整依赖")
    void explicitArgumentFunctionKeepsCompleteCoverage() {
        CompiledExpression expression = compile(
                new FactAccessFunction(FunctionFactAccess.EXPLICIT_ARGUMENTS_ONLY));

        assertThat(expression.dependencyCoverage()).isEqualTo(DependencyCoverage.COMPLETE);
    }

    /** 能从调用上下文读取其他事实的函数必须触发保守路由。 */
    @Test
    @DisplayName("动态事实函数标记依赖不完整")
    void dynamicFunctionMarksCoverageDynamic() {
        CompiledExpression expression = compile(
                new FactAccessFunction(FunctionFactAccess.DYNAMIC_FACTS));

        assertThat(expression.dependencyCoverage()).isEqualTo(DependencyCoverage.DYNAMIC);
    }

    /** 事实访问声明属于函数目录和执行环境语义，变化后旧产物不能复用。 */
    @Test
    @DisplayName("事实访问声明参与执行环境摘要")
    void factAccessChangesExecutionEnvironmentDigest() {
        CompiledExpression explicit = compile(
                new FactAccessFunction(FunctionFactAccess.EXPLICIT_ARGUMENTS_ONLY));
        CompiledExpression dynamic = compile(
                new FactAccessFunction(FunctionFactAccess.DYNAMIC_FACTS));

        assertThat(explicit.environmentDigest()).isNotEqualTo(dynamic.environmentDigest());
        assertThat(explicit.artifactDigest()).isNotEqualTo(dynamic.artifactDigest());
    }

    /** 使用指定事实访问声明编译同一函数表达式。 */
    private static CompiledExpression compile(RuleFunction function) {
        return ExpressionEngine.builder()
                .registerFunction(function)
                .build()
                .compile("$ACCESS()", FactContract.builder("coverage-v1").build())
                .requireCompiled();
    }

    /** 返回固定布尔值、只用于声明事实访问方式的测试函数。 */
    private static final class FactAccessFunction implements RuleFunction {
        private final FunctionFactAccess factAccess;

        /** 保存测试希望验证的事实访问声明。 */
        private FactAccessFunction(FunctionFactAccess factAccess) {
            this.factAccess = factAccess;
        }

        /** {@inheritDoc} */
        @Override public String code() { return "ACCESS"; }

        /** {@inheritDoc} */
        @Override public String semanticVersion() { return "1"; }

        /** {@inheritDoc} */
        @Override public FunctionSignature signature() { return FunctionSignature.empty(); }

        /** {@inheritDoc} */
        @Override
        public TypeDescriptor returnType() {
            return TypeDescriptor.scalar(TypeKind.BOOLEAN, false);
        }

        /** {@inheritDoc} */
        @Override
        public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(
                    FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE,
                    FunctionThreading.THREAD_SAFE);
        }

        /** {@inheritDoc} */
        @Override public FunctionFactAccess factAccess() { return factAccess; }

        /** {@inheritDoc} */
        @Override
        public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            return FactValues.booleanValue(true);
        }
    }
}
