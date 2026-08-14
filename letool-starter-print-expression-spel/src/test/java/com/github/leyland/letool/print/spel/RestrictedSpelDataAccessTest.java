package com.github.leyland.letool.print.spel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.xml.expression.ExpressionCompileContext;
import com.github.leyland.letool.print.xml.expression.ExpressionEvaluationContext;
import com.github.leyland.letool.print.xml.expression.PrintExpressionPlan;
import com.github.leyland.letool.print.xml.extension.PrintDataView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受限 SpEL 只读数据、作用域和类型边界测试。
 *
 * @author leyland
 */
class RestrictedSpelDataAccessTest {

    /** 测试使用的 JSON 解析器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 待测试的受限 SpEL 提供方。 */
    private final RestrictedSpelConditionExpression expression =
            new RestrictedSpelConditionExpression();

    /**
     * 验证循环变量优先于同名根属性。
     */
    @Test
    void shouldPreferLoopVariableOverRootProperty() throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree("""
                {"item":{"status":"ROOT"}}
                """);
        JsonNode variable = OBJECT_MAPPER.readTree("""
                {"status":"VARIABLE"}
                """);
        PrintDataView data = PrintDataView.of(root, Map.of("item", variable));

        assertThat(evaluate("item.status == 'VARIABLE'", data)).isTrue();
    }

    /**
     * 验证数据视图显式替换变量后，表达式读取最新不可变快照。
     */
    @Test
    void shouldUseLatestVariableSnapshotProvidedByDataView() throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree("{}");
        JsonNode outer = OBJECT_MAPPER.readTree("{\"value\":\"OUTER\"}");
        JsonNode inner = OBJECT_MAPPER.readTree("{\"value\":\"INNER\"}");
        PrintDataView data = PrintDataView.of(root, Map.of("item", outer))
                .withVariable("item", inner);

        assertThat(evaluate("item.value == 'INNER'", data)).isTrue();
    }

    /**
     * 验证显式 JSON 空值可以比较，而缺失属性会安全失败。
     */
    @Test
    void shouldDistinguishExplicitNullFromMissingProperty() throws Exception {
        PrintDataView explicitNull = data("{\"remark\":null}");
        PrintDataView missing = data("{}");

        assertThat(evaluate("remark == null", explicitNull)).isTrue();
        assertThatThrownBy(() -> evaluate("remark == null", missing))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证不能从标量继续读取属性，也不能把对象当作数组访问。
     */
    @Test
    void shouldRejectTraversalThatDoesNotMatchJsonShape() throws Exception {
        PrintDataView data = data("""
                {"name":"张三","patient":{"name":"李四"}}
                """);

        assertThatThrownBy(() -> evaluate("name.length == 2", data))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> evaluate("patient[0] == null", data))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证数组越界不会降级为 null 或访问其他对象。
     */
    @Test
    void shouldRejectArrayIndexOutOfBounds() throws Exception {
        PrintDataView data = data("{\"items\":[true]}");

        assertThatThrownBy(() -> evaluate("items[1] == true", data))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证字符串、数字和布尔值之间不执行宽松真值或等值转换。
     */
    @Test
    void shouldNotCoerceDifferentScalarTypes() throws Exception {
        PrintDataView data = data("{\"count\":2,\"enabled\":true}");

        assertThat(evaluate("count == '2'", data)).isFalse();
        assertThat(evaluate("enabled == 'true'", data)).isFalse();
        assertThatThrownBy(() -> evaluate("count < '3'", data))
                .isInstanceOf(PrintValidationException.class);
    }

    /**
     * 验证表达式最终结果必须是布尔值。
     */
    @Test
    void shouldRejectNonBooleanResult() throws Exception {
        PrintDataView data = data("""
                {"name":"张三","count":1,"patient":{},"items":[]}
                """);

        for (String nonBoolean : new String[]{
                "name", "count", "patient", "items", "null"}) {
            // 每种合法值类型都单独求值，防止未来新增转换规则后绕过严格布尔结果约束。
            assertThatThrownBy(() -> evaluate(nonBoolean, data))
                    .as("应拒绝非布尔结果：%s", nonBoolean)
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("条件表达式结果必须为布尔值");
        }
    }

    /**
     * 验证越界读取不会自动扩展原始数组，也不会修改调用方提供的数据。
     */
    @Test
    void shouldNotAutoGrowArrays() throws Exception {
        JsonNode source = OBJECT_MAPPER.readTree("{\"items\":[true]}");
        PrintDataView data = PrintDataView.of(source, Map.of());

        assertThatThrownBy(() -> evaluate("items[1] == true", data))
                .isInstanceOf(PrintValidationException.class);
        assertThat(source.path("items")).hasSize(1);
    }

    /**
     * 验证求值失败不会公开业务值、属性名或 Spring 实现类型。
     */
    @Test
    void shouldSanitizeEvaluationFailure() throws Exception {
        String secret = "secret-business-value";
        PrintDataView data = data("{\"privateField\":\"" + secret + "\"}");

        assertThatThrownBy(() -> evaluate("missingField == '" + secret + "'", data))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("missingField")
                .hasMessageNotContaining("RestrictedSpelDataNode")
                .hasNoCause();
    }

    /**
     * 编译并执行指定表达式。
     *
     * @param source 表达式正文
     * @param data 当前只读打印数据
     * @return 条件结果
     */
    private boolean evaluate(String source, PrintDataView data) {
        PrintExpressionPlan plan = expression.compile(new ExpressionCompileContext(
                "spel", source, "数据测试位置"));
        return plan.evaluate(new ExpressionEvaluationContext(data));
    }

    /**
     * 从 JSON 正文创建无循环变量的数据视图。
     *
     * @param json JSON 根对象正文
     * @return 防御性打印数据视图
     * @throws Exception JSON 解析失败时抛出
     */
    private PrintDataView data(String json) throws Exception {
        return PrintDataView.of(OBJECT_MAPPER.readTree(json), Map.of());
    }
}
