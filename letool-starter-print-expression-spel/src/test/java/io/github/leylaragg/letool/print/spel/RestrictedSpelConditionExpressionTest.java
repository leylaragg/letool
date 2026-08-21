package io.github.leylaragg.letool.print.spel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.ExpressionEvaluationContext;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.xml.extension.PrintDataView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受限 SpEL 条件表达式正常语义测试。
 *
 * @author leyland
 */
class RestrictedSpelConditionExpressionTest {

    /** 测试使用的 JSON 解析器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 待测试的受限 SpEL 提供方。 */
    private final RestrictedSpelConditionExpression expression =
            new RestrictedSpelConditionExpression();

    /**
     * 验证提供方暴露稳定的小写语言名称。
     */
    @Test
    void shouldExposeStableLanguageName() {
        assertThat(expression.language()).isEqualTo("spel");
    }

    /**
     * 验证根属性、嵌套属性、字面量、比较与布尔组合能够共同求值。
     */
    @Test
    void shouldEvaluatePropertiesLiteralsComparisonsAndBooleanOperators() throws Exception {
        PrintExpressionPlan plan = compile("""
                enabled == true && (patient.age >= 18 || patient.name == '儿童')
                """);

        assertThat(evaluate(plan, """
                {"enabled":true,"patient":{"age":20,"name":"张三"}}
                """)).isTrue();
        assertThat(evaluate(plan, """
                {"enabled":true,"patient":{"age":8,"name":"儿童"}}
                """)).isTrue();
        assertThat(evaluate(plan, """
                {"enabled":false,"patient":{"age":20,"name":"张三"}}
                """)).isFalse();
    }

    /**
     * 验证整数、小数、字符串、布尔和空值字面量的受限语义。
     */
    @Test
    void shouldEvaluateSupportedLiteralTypes() throws Exception {
        PrintExpressionPlan plan = compile("""
                count == 2 && amount < 10.5 && name != '李四'
                        && active == true && remark == null
                """);

        assertThat(evaluate(plan, """
                {"count":2,"amount":9.75,"name":"张三","active":true,"remark":null}
                """)).isTrue();
    }

    /**
     * 验证逻辑非、完整比较运算符和括号优先级。
     */
    @Test
    void shouldEvaluateNotAndAllComparisonOperators() throws Exception {
        PrintExpressionPlan plan = compile("""
                !(score < 60) && score <= 100 && score > 0
                        && score >= 60 && score != 80 && score == 90
                """);

        assertThat(evaluate(plan, "{\"score\":90}")).isTrue();
    }

    /**
     * 验证数组下标与下标后的属性读取。
     */
    @Test
    void shouldReadArrayIndexAndNestedProperty() throws Exception {
        PrintExpressionPlan plan = compile("items[0].enabled && items[1].name == '备用'");

        assertThat(evaluate(plan, """
                {"items":[{"enabled":true},{"name":"备用"}]}
                """)).isTrue();
        assertThat(plan.inspectionContribution().dataPaths())
                .containsExactly("items[0].enabled", "items[1].name");
    }

    /**
     * 验证同一个不可变编译计划可以绑定不同数据上下文。
     */
    @Test
    void shouldReuseCompiledPlanAcrossDataViews() throws Exception {
        PrintExpressionPlan plan = compile("status == 'ACTIVE'");

        assertThat(evaluate(plan, "{\"status\":\"ACTIVE\"}")).isTrue();
        assertThat(evaluate(plan, "{\"status\":\"DISABLED\"}")).isFalse();
    }

    /**
     * 编译测试表达式。
     *
     * @param source 表达式正文
     * @return 可重复求值的表达式计划
     */
    private PrintExpressionPlan compile(String source) {
        return expression.compile(new ExpressionCompileContext(
                "spel", source.strip(), "测试条件位置"));
    }

    /**
     * 使用标准 JSON 根对象执行表达式计划。
     *
     * @param plan 表达式计划
     * @param json JSON 根对象正文
     * @return 条件求值结果
     * @throws Exception JSON 解析失败时抛出
     */
    private boolean evaluate(PrintExpressionPlan plan, String json) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        PrintDataView data = PrintDataView.of(root, Map.of());
        return plan.evaluate(new ExpressionEvaluationContext(data));
    }
}
