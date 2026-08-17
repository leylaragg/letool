package io.github.leylaragg.letool.print.spel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.ExpressionEvaluationContext;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.xml.extension.PrintDataView;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受限 SpEL 单次求值访问次数和软截止时间测试。
 *
 * @author leyland
 */
class RestrictedSpelBudgetTest {

    /** 测试使用的 JSON 解析器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证访问次数恰好达到上限时允许，下一次访问稳定拒绝。
     */
    @Test
    void shouldAllowExactStepLimitAndRejectNextStep() {
        RestrictedSpelBudget budget = new RestrictedSpelBudget(
                2, 1_000, () -> 0L);

        assertThatCode(() -> {
            budget.checkpoint();
            budget.checkpoint();
        }).doesNotThrowAnyException();
        assertThatThrownBy(budget::checkpoint)
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证单调时钟超过软截止时间后安全失败。
     */
    @Test
    void shouldRejectAfterSoftDeadline() {
        AtomicLong clock = new AtomicLong();
        RestrictedSpelBudget budget = new RestrictedSpelBudget(
                10, 50, clock::get);
        clock.set(51);

        assertThatThrownBy(budget::checkpoint)
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证真实表达式属性读取和求值边界都计入同一次预算。
     */
    @Test
    void shouldCountRealEvaluationSteps() throws Exception {
        RestrictedSpelConditionExpression allowed = new RestrictedSpelConditionExpression(
                () -> new RestrictedSpelBudget(4, 1_000, () -> 0L));
        RestrictedSpelConditionExpression rejected = new RestrictedSpelConditionExpression(
                () -> new RestrictedSpelBudget(3, 1_000, () -> 0L));
        PrintDataView data = PrintDataView.of(OBJECT_MAPPER.readTree("""
                {"patient":{"name":"张三"}}
                """), Map.of());

        assertThat(evaluate(allowed, data)).isTrue();
        assertThatThrownBy(() -> evaluate(rejected, data))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证真实表达式通过可控单调时钟执行软截止检查。
     */
    @Test
    void shouldCheckDeadlineDuringRealEvaluation() throws Exception {
        AtomicLong clock = new AtomicLong();
        RestrictedSpelConditionExpression expression = new RestrictedSpelConditionExpression(
                () -> new RestrictedSpelBudget(10, 50,
                        () -> clock.getAndAdd(100)));
        PrintDataView data = PrintDataView.of(
                OBJECT_MAPPER.readTree("{\"enabled\":true}"), Map.of());

        assertThatThrownBy(() -> evaluate(expression, data))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证提供方公开 SPI 会统一脱敏预算工厂等内部运行时异常。
     */
    @Test
    void shouldSanitizeUnexpectedRuntimeFailure() throws Exception {
        String secret = "secret-business-value";
        RestrictedSpelConditionExpression expression =
                new RestrictedSpelConditionExpression(() -> {
                    throw new IllegalStateException(secret);
                });
        PrintExpressionPlan plan = expression.compile(new ExpressionCompileContext(
                "spel", "enabled == true", "预算异常测试位置"));
        PrintDataView data = PrintDataView.of(
                OBJECT_MAPPER.readTree("{\"enabled\":true}"), Map.of());

        assertThatThrownBy(() -> plan.evaluate(
                new ExpressionEvaluationContext(data)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("IllegalStateException")
                .hasNoCause();
    }

    /**
     * 编译并执行固定的测试条件。
     *
     * @param expression 待测试表达式提供方
     * @param data 当前只读打印数据
     * @return 条件结果
     */
    private boolean evaluate(
            RestrictedSpelConditionExpression expression, PrintDataView data) {
        PrintExpressionPlan plan = expression.compile(new ExpressionCompileContext(
                "spel", "patient.name == '张三'", "预算测试位置"));
        return plan.evaluate(new ExpressionEvaluationContext(data));
    }
}
