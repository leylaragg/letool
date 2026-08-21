package io.github.leylaragg.letool.print.xml.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import io.github.leylaragg.letool.print.xml.extension.PrintDataView;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 条件表达式注册表和只读数据视图契约测试。
 *
 * @author leyland
 */
class PrintExpressionRegistryTest {

    /** 表达式计划必须主动说明静态读取，不能依靠默认空贡献掩盖路径。 */
    @Test
    void shouldRequireExplicitInspectionContribution() throws Exception {
        assertThat(PrintExpressionPlan.class.isAnnotationPresent(FunctionalInterface.class))
                .isFalse();
        assertThat(PrintExpressionPlan.class.getMethod("inspectionContribution").isDefault())
                .isFalse();
    }

    /** 验证注册表会冻结调用方集合并按语言名查找。 */
    @Test
    void shouldCreateImmutableExpressionSnapshot() {
        AtomicReference<String> language = new AtomicReference<>("demo");
        List<PrintConditionExpression> expressions = new ArrayList<>();
        expressions.add(expression(language));
        PrintExpressionRegistry registry = new PrintExpressionRegistry(expressions);

        expressions.clear();
        language.set("changed");

        assertThat(registry.require("demo").language()).isEqualTo("demo");
        assertThat(registry.languages()).containsExactly("demo");
        assertThatThrownBy(() -> registry.languages().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证非法名称、重复语言和未知语言均会立即失败。 */
    @Test
    void shouldRejectInvalidDuplicateAndMissingLanguages() {
        assertThatThrownBy(() -> new PrintExpressionRegistry(List.of(expression("SpEL"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
        assertThatThrownBy(() -> new PrintExpressionRegistry(List.of(
                expression("demo"), expression("demo"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        PrintExpressionRegistry registry = new PrintExpressionRegistry(List.of());
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    /** 验证数据视图在输入和输出边界都执行防御性复制。 */
    @Test
    void shouldProtectDataViewFromMutation() {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("status", "ACTIVE");
        ObjectNode item = JsonNodeFactory.instance.objectNode().put("name", "first");
        PrintDataView view = PrintDataView.of(root, Map.of("item", item));

        root.put("status", "CHANGED");
        item.put("name", "changed");
        ((ObjectNode) view.root()).put("status", "RETURNED");
        ((ObjectNode) view.variable("item").orElseThrow()).put("name", "RETURNED");

        assertThat(view.root().path("status").asText()).isEqualTo("ACTIVE");
        assertThat(view.variable("item").orElseThrow().path("name").asText()).isEqualTo("first");
        assertThat(view.variableNames()).containsExactly("item");
        assertThat(view.variable("missing")).isEmpty();
    }

    /** 验证派生词法变量视图不会修改父视图或共享调用方可变节点。 */
    @Test
    void shouldDeriveChildDataViewSafely() {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("status", "ACTIVE");
        ObjectNode item = JsonNodeFactory.instance.objectNode().put("name", "first");
        PrintDataView parent = PrintDataView.of(root, Map.of());

        PrintDataView child = parent.withVariable("item", item);
        item.put("name", "changed");

        assertThat(parent.variable("item")).isEmpty();
        assertThat(child.root().path("status").asText()).isEqualTo("ACTIVE");
        assertThat(child.variable("item").orElseThrow().path("name").asText()).isEqualTo("first");
    }

    /** 验证任意 POJO 节点不能穿透防御性 JSON 数据边界。 */
    @Test
    void shouldRejectPojoNodes() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.set("business", new POJONode(new MutableBusinessValue("secret")));

        assertThatThrownBy(() -> PrintDataView.of(root, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标准 JSON");
    }

    /** 创建无状态测试表达式提供方。 */
    private static PrintConditionExpression expression(String language) {
        return expression(new AtomicReference<>(language));
    }

    /** 创建可变元数据测试表达式提供方。 */
    private static PrintConditionExpression expression(AtomicReference<String> language) {
        return new PrintConditionExpression() {
            @Override
            public String language() {
                return language.get();
            }

            @Override
            public PrintExpressionPlan compile(ExpressionCompileContext context) {
                return PrintExpressionPlan.of(
                        TemplateInspectionContribution.empty(), evaluation -> true);
            }
        };
    }

    /** 用于证明 POJO 节点仍持有原业务对象的可变测试值。 */
    private static final class MutableBusinessValue {

        /** 测试业务值。 */
        private String value;

        /** 创建可变测试业务值。 */
        private MutableBusinessValue(String value) {
            this.value = value;
        }

        /** @return 当前测试业务值 */
        private String value() {
            return value;
        }
    }
}
