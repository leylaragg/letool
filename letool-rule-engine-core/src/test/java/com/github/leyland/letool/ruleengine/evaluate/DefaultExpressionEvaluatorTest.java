package com.github.leyland.letool.ruleengine.evaluate;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.fact.FactValue;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;
import com.github.leyland.letool.ruleengine.function.FunctionRegistry;
import com.github.leyland.letool.ruleengine.type.FactContract;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultExpressionEvaluatorTest {

    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private static final TypeDescriptor DECIMAL = TypeDescriptor.scalar(TypeKind.DECIMAL, false);
    private static final TypeDescriptor BOOLEAN = TypeDescriptor.scalar(TypeKind.BOOLEAN, false);
    private static final TypeDescriptor STRING = TypeDescriptor.scalar(TypeKind.STRING, true);
    private final FunctionRegistry registry = FunctionRegistry.builder().build();
    private final FactContract contract = FactContract.builder("evaluation-1")
            .path("amount", DECIMAL)
            .path("active", BOOLEAN)
            .path("name", STRING)
            .path("level", TypeDescriptor.scalar(TypeKind.STRING, false))
            .path("nullable", TypeDescriptor.scalar(TypeKind.INTEGER, true))
            .build();
    private final RuleFacts facts = RuleFacts.fromMap(Map.of(
            "amount", new BigDecimal("125.50"),
            "active", true,
            "name", "Ada",
            "level", "HIGH",
            "nullable", 1));
    private final ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1 + 2 * 3|7",
            "-5 + +2|-3",
            "7 % 4|3",
            "7 / 2|3",
            "${amount} >= 100 AND ${active}|true",
            "${name} IS NOT NULL|true",
            "${level} IN ('HIGH', 'CRITICAL')|true",
            "${level} NOT IN ('LOW', 'MEDIUM')|true",
            "${amount} BETWEEN 100 AND 130|true"
    })
    @DisplayName("编译表达式应按标量语义求值")
    void shouldEvaluateCompiledExpression(String source, String expected) {
        ExpressionEvaluationResult result = evaluator.evaluate(
                compile(source, contract, registry), facts, registry, EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.requireValue().toString()).isEqualTo(expected);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("小数除法应固定使用DECIMAL128且时间值严格按各自类型比较")
    void shouldUseDecimal128AndCompareTemporalValues() {
        assertThat(value("1.0 / 3.0", FactContract.builder("empty").build(), RuleFacts.fromMap(Map.of())))
                .isEqualTo(new BigDecimal("0.3333333333333333333333333333333333"));
        assertThat(value("DATE '2026-08-14' > DATE '2026-08-13'",
                FactContract.builder("empty").build(), RuleFacts.fromMap(Map.of()))).isEqualTo(true);
        assertThat(value("DATETIME '2026-08-14T10:30:00' = DATETIME '2026-08-14T10:30:00'",
                FactContract.builder("empty").build(), RuleFacts.fromMap(Map.of()))).isEqualTo(true);
        assertThat(value("INSTANT '2026-08-14T02:30:00Z' <= INSTANT '2026-08-14T03:00:00Z'",
                FactContract.builder("empty").build(), RuleFacts.fromMap(Map.of()))).isEqualTo(true);
    }

    @Test
    @DisplayName("空值只参与相等和显式空判断")
    void shouldApplyNullMatrix() {
        FactContract empty = FactContract.builder("empty").build();
        RuleFacts noFacts = RuleFacts.fromMap(Map.of());

        assertThat(value("null = null", empty, noFacts)).isEqualTo(true);
        assertThat(value("null != 1", empty, noFacts)).isEqualTo(true);
        assertThat(value("null IS NULL", empty, noFacts)).isEqualTo(true);
        assertThat(value("1 IS NOT NULL", empty, noFacts)).isEqualTo(true);
    }

    @Test
    @DisplayName("除零应返回稳定运行期失败而不是顶层抛出")
    void shouldReturnFailureForDivisionByZero() {
        ExpressionEvaluationResult result = evaluator.evaluate(
                compile("10 / 0", FactContract.builder("empty").build(), registry),
                RuleFacts.fromMap(Map.of()), registry, EvaluationOptions.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(RuleDiagnosticCode.EVALUATION_ERROR);
            assertThat(diagnostic.phase()).isEqualTo(DiagnosticPhase.RUNTIME);
            assertThat(diagnostic.arguments()).isEmpty();
        });
        assertThat(result.failureCause().getErrorCode()).isSameAs(RuleEngineErrorCode.EVALUATION_FAILED);
        assertThatThrownBy(result::requireValue).isSameAs(result.failureCause());
    }

    @Test
    @DisplayName("求值前应只校验编译产物声明的依赖路径、类型和可空性")
    void shouldValidateOnlyTypedDependencies() {
        CompiledExpression expression = compile("${amount} > 10", contract, registry);

        ExpressionEvaluationResult missing = evaluator.evaluate(expression,
                RuleFacts.fromMap(Map.of("unrelated", new Object[] {1, 2})),
                registry, EvaluationOptions.defaults());
        ExpressionEvaluationResult wrongType = evaluator.evaluate(expression,
                RuleFacts.fromMap(Map.of("amount", "secret")),
                registry, EvaluationOptions.defaults());
        Map<String, Object> nullAmount = new HashMap<>();
        nullAmount.put("unrelated", 1);
        nullAmount.put("amount", null);
        ExpressionEvaluationResult nullForRequired = evaluator.evaluate(expression,
                RuleFacts.fromMap(nullAmount),
                registry, EvaluationOptions.defaults());

        assertThat(missing.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.MISSING_FACT_VALUE);
        assertThat(wrongType.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
        assertThat(nullForRequired.diagnostics()).extracting(d -> d.code())
                .containsExactly(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH);
        assertThat(wrongType.diagnostics().get(0).arguments()).doesNotContain("secret");
    }

    @Test
    @DisplayName("可空依赖接收显式空值并按空值表达式求值")
    void shouldAcceptNullForNullableDependency() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", null);

        ExpressionEvaluationResult result = evaluator.evaluate(
                compile("${name} IS NULL", contract, registry), RuleFacts.fromMap(input),
                registry, EvaluationOptions.defaults());

        assertThat(result.requireBoolean()).isTrue();
    }

    @Test
    @DisplayName("求值公开入口的空参数属于API误用并应直接抛出")
    void shouldRejectNullApiArguments() {
        CompiledExpression expression = compile(
                "true", FactContract.builder("empty").build(), registry);
        RuleFacts noFacts = RuleFacts.fromMap(Map.of());

        assertThatThrownBy(() -> evaluator.evaluate(null, noFacts, registry, EvaluationOptions.defaults()))
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> evaluator.evaluate(expression, null, registry, EvaluationOptions.defaults()))
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> evaluator.evaluate(expression, noFacts, null, EvaluationOptions.defaults()))
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> evaluator.evaluate(expression, noFacts, registry, null))
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("求值选项应按全部限制维度提供值语义并拒绝空组件")
    void shouldProvideEvaluationOptionValueSemantics() {
        EvaluationOptions first = EvaluationOptions.of(java.util.Locale.CHINA,
                java.time.ZoneId.of("Asia/Shanghai"), true, EngineLimits.defaults());
        EvaluationOptions second = EvaluationOptions.of(java.util.Locale.CHINA,
                java.time.ZoneId.of("Asia/Shanghai"), true, EngineLimits.defaults());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(EvaluationOptions.defaults());
        assertThatThrownBy(() -> EvaluationOptions.of(
                null, java.time.ZoneId.of("UTC"), false, EngineLimits.defaults()))
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("成功结果应验证布尔读取并保持结果不可变契约")
    void shouldRequireBooleanSafely() {
        ExpressionEvaluationResult booleanResult = evaluator.evaluate(
                compile("true", FactContract.builder("empty").build(), registry),
                RuleFacts.fromMap(Map.of()), registry, EvaluationOptions.defaults());
        ExpressionEvaluationResult integerResult = evaluator.evaluate(
                compile("1", FactContract.builder("empty").build(), registry),
                RuleFacts.fromMap(Map.of()), registry, EvaluationOptions.defaults());

        assertThat(booleanResult.requireBoolean()).isTrue();
        assertThatThrownBy(integerResult::requireBoolean)
                .hasFieldOrPropertyWithValue("errorCode", RuleEngineErrorCode.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("最大合法AST深度应通过显式求值栈完成而不依赖Java递归栈")
    void shouldEvaluateMaximumConfiguredDepthIteratively() {
        StringBuilder source = new StringBuilder("1");
        for (int index = 0; index < 120; index++) {
            source.insert(0, "+(").append(')');
        }
        EngineLimits limits = new EngineLimits(1_000, 1_000, 256, 10, 10, 20);
        CompiledExpression compiled = new DefaultExpressionCompiler().compile(
                source.toString(), FactContract.builder("empty").build(), registry, limits)
                .requireCompiled();

        ExpressionEvaluationResult result = evaluator.evaluate(
                compiled, RuleFacts.fromMap(Map.of()), registry,
                EvaluationOptions.of(java.util.Locale.ROOT,
                        java.time.ZoneId.of("UTC"), false, limits));

        assertThat(result.requireValue().toSafeJavaValue()).isEqualTo(BigInteger.ONE);
    }

    private Object value(String source, FactContract factContract, RuleFacts ruleFacts) {
        FactValue result = evaluator.evaluate(compile(source, factContract, registry), ruleFacts,
                registry, EvaluationOptions.defaults()).requireValue();
        return result.toSafeJavaValue();
    }

    private static CompiledExpression compile(
            String source, FactContract factContract, FunctionRegistry functionRegistry) {
        return new DefaultExpressionCompiler().compile(source, factContract, functionRegistry,
                EngineLimits.defaults()).requireCompiled();
    }
}
