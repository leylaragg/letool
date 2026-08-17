package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.compile.DefaultExpressionCompiler;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationTraceTest {

    private final FunctionRegistry registry = FunctionRegistry.builder().build();
    private final FactContract emptyContract = FactContract.builder("empty").build();

    @Test
    @DisplayName("默认关闭轨迹时应复用空单例且不创建节点列表")
    void shouldReuseDisabledTrace() {
        ExpressionEvaluationResult first = evaluate("1 + 2", EvaluationOptions.defaults());
        ExpressionEvaluationResult second = evaluate("3 + 4", EvaluationOptions.defaults());

        assertThat(first.trace()).isSameAs(EvaluationTrace.disabled());
        assertThat(second.trace()).isSameAs(first.trace());
        assertThat(first.trace().nodes()).isEmpty();
        assertThat(first.trace().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("开启轨迹应记录节点范围、结果分类、类型和有界值摘要")
    void shouldRecordSafeTraceNodes() {
        EngineLimits limits = new EngineLimits(100, 100, 20, 10, 100, 8);
        ExpressionEvaluationResult result = evaluate("'abcdefghijkl' = 'abcdefghijkl'",
                EvaluationOptions.of(Locale.ROOT, ZoneId.of("UTC"), true, limits));

        assertThat(result.requireBoolean()).isTrue();
        assertThat(result.trace().isEnabled()).isTrue();
        assertThat(result.trace().nodes()).isNotEmpty().allSatisfy(node -> {
            assertThat(node.nodeType()).isNotBlank();
            assertThat(node.startPosition()).isGreaterThanOrEqualTo(0);
            assertThat(node.endPosition()).isGreaterThan(node.startPosition());
            assertThat(node.resultCategory()).isEqualTo(TraceNode.ResultCategory.VALUE);
            assertThat(node.type()).isNotNull();
            assertThat(node.summary()).hasSizeLessThanOrEqualTo(8);
        });
        assertThat(result.trace().nodes()).anySatisfy(node ->
                assertThat(node.summary()).doesNotContain("abcdefghijkl"));
    }

    @Test
    @DisplayName("摘要器不得调用任意原始对象toString且容器只显示类型和大小")
    void shouldNeverInvokeRawObjectToString() {
        Object hostile = new Object() {
            @Override public String toString() { throw new AssertionError("不得调用"); }
        };
        DefaultValueSummarizer summarizer = new DefaultValueSummarizer();

        assertThat(summarizer.summarize(FactValues.fromJavaValue(Map.of("value", 1)), 64))
                .isEqualTo("OBJECT(size=1)");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FactValues.fromJavaValue(hostile))
                .isInstanceOf(io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.class);
        assertThat(summarizer.summarize(FactValues.integer(BigInteger.ONE.shiftLeft(20_000)), 64))
                .startsWith("INTEGER(bits=").hasSizeLessThanOrEqualTo(64);
        assertThat(summarizer.summarize(FactValues.string("x".repeat(100_000)), 16))
                .hasSizeLessThanOrEqualTo(16);
    }

    @Test
    @DisplayName("自定义摘要器异常和超长输出应被框架净化并截断")
    void shouldBoundUntrustedSummarizer() {
        EngineLimits limits = new EngineLimits(100, 100, 20, 10, 20, 6);
        EvaluationOptions options = EvaluationOptions.of(
                Locale.ROOT, ZoneId.of("UTC"), true, limits);
        CompiledExpression compiled = new DefaultExpressionCompiler().compile(
                "1 + 2", emptyContract, registry, limits).requireCompiled();
        ValueSummarizer hostile = (value, maximum) -> "secret".repeat(100);

        ExpressionEvaluationResult result = new DefaultExpressionEvaluator(hostile).evaluate(
                compiled, RuleFacts.fromMap(Map.of()), registry, options);

        assertThat(result.requireValue().toSafeJavaValue()).isEqualTo(java.math.BigInteger.valueOf(3));
        assertThat(result.trace().nodes()).allSatisfy(node ->
                assertThat(node.summary()).hasSizeLessThanOrEqualTo(6));
    }

    @Test
    @DisplayName("自定义摘要器中的控制字符应被净化为单行安全文本")
    void shouldSanitizeControlCharactersFromCustomSummarizer() {
        EngineLimits limits = new EngineLimits(100, 100, 20, 10, 20, 30);
        EvaluationOptions options = EvaluationOptions.of(
                Locale.ROOT, ZoneId.of("UTC"), true, limits);
        CompiledExpression compiled = new DefaultExpressionCompiler().compile(
                "1", emptyContract, registry, limits).requireCompiled();

        ExpressionEvaluationResult result = new DefaultExpressionEvaluator(
                (value, maximum) -> "safe\r\nsecret\u0000").evaluate(
                compiled, RuleFacts.fromMap(Map.of()), registry, options);

        assertThat(result.trace().nodes()).singleElement().satisfies(node ->
                assertThat(node.summary()).doesNotContain("\r", "\n", "\u0000"));
    }

    @Test
    @DisplayName("轨迹节点达到上限后应截断但不改变求值结果")
    void shouldTruncateTraceWithoutChangingResult() {
        EngineLimits oneNode = new EngineLimits(100, 100, 20, 10, 1, 20);
        ExpressionEvaluationResult result = evaluate("1 + 2 * 3",
                EvaluationOptions.of(Locale.ROOT, ZoneId.of("UTC"), true, oneNode));

        assertThat(result.requireValue().toSafeJavaValue()).isEqualTo(java.math.BigInteger.valueOf(7));
        assertThat(result.trace().nodes()).hasSize(1);
        assertThat(result.trace().isTruncated()).isTrue();
    }

    @Test
    @DisplayName("失败路径启用轨迹时应记录安全失败节点")
    void shouldTraceFailureSafely() {
        EngineLimits limits = new EngineLimits(100, 100, 20, 10, 20, 20);
        ExpressionEvaluationResult result = evaluate("1 / 0",
                EvaluationOptions.of(Locale.ROOT, ZoneId.of("UTC"), true, limits));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.trace().nodes()).anySatisfy(node -> {
            assertThat(node.resultCategory()).isEqualTo(TraceNode.ResultCategory.FAILURE);
            assertThat(node.summary()).isEqualTo("FAILURE");
        });
    }

    private ExpressionEvaluationResult evaluate(String source, EvaluationOptions options) {
        CompiledExpression compiled = new DefaultExpressionCompiler().compile(
                source, emptyContract, registry, options.limits()).requireCompiled();
        return new DefaultExpressionEvaluator().evaluate(
                compiled, RuleFacts.fromMap(Map.of()), registry, options);
    }
}
