package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.PrintConditionExpression;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 条件表达式扩展的编译和绑定契约测试。
 *
 * @author leyland
 */
class XmlExpressionExtensionTest {

    /** 验证显式注册的表达式计划能够读取根数据并控制块展开。 */
    @Test
    void shouldCompileAndEvaluateRegisteredExpression() {
        PrintConditionExpression expression = expression("demo", context -> evaluation ->
                evaluation.data().root().path(context.expression()).asBoolean());
        XmlTemplateCompiler compiler = compiler(expression);
        CompiledXmlTemplate template = compile(compiler, """
                <page>
                    <if expression-language="demo" test="enabled">
                        <paragraph>显示</paragraph>
                    </if>
                </page>
                """);

        DocumentModel shown = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("enabled", true)));
        DocumentModel hidden = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("enabled", false)));

        assertThat(shown.blocks()).containsExactly(
                new ParagraphNode("", List.of(new TextNode("显示"))));
        assertThat(hidden.blocks()).isEmpty();
    }

    /** 验证循环变量通过只读数据视图提供给表达式计划。 */
    @Test
    void shouldExposeLoopVariablesThroughReadOnlyView() throws Exception {
        PrintConditionExpression expression = expression("demo", context -> evaluation ->
                evaluation.data().variable("item").orElseThrow()
                        .path(context.expression()).asBoolean());
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><for-each items="items" var="item">
                    <if expression-language="demo" test="visible">
                        <paragraph><field path="$item.name"/></paragraph>
                    </if>
                </for-each></page>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(template, PrintContext.of(1,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"items":[{"name":"A","visible":true},{"name":"B","visible":false}]}
                        """)));

        assertThat(model.blocks()).containsExactly(
                new ParagraphNode("", List.of(new TextNode("A"))));
    }

    /** 验证未注册、缺失配对、结构化混用和空表达式在编译期失败。 */
    @Test
    void shouldRejectInvalidExpressionDeclarations() {
        XmlTemplateCompiler empty = new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
        List<String> pages = List.of(
                "<page><if expression-language=\"missing\" test=\"enabled\"><paragraph>x</paragraph></if></page>",
                "<page><if expression-language=\"demo\"><paragraph>x</paragraph></if></page>",
                "<page><if test=\"enabled\"><paragraph>x</paragraph></if></page>",
                "<page><if expression-language=\"demo\" test=\"enabled\" path=\"enabled\"><paragraph>x</paragraph></if></page>",
                "<page><if expression-language=\"demo\" test=\"\"><paragraph>x</paragraph></if></page>",
                "<page><if expression-language=\"demo\" test=\""
                        + "x".repeat(XmlDsl.MAX_EXPRESSION_CHARACTERS + 1)
                        + "\"><paragraph>x</paragraph></if></page>");

        for (String page : pages) {
            XmlTemplateCompiler compiler = page.contains("missing") ? empty : compiler(
                    expression("demo", context -> evaluation -> true));
            assertThatThrownBy(() -> compile(compiler, page))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证提供方编译异常不会回显表达式正文和底层消息。 */
    @Test
    void shouldSanitizeExpressionCompilationFailure() {
        PrintConditionExpression expression = expression("demo", context -> {
            throw new IllegalArgumentException("secret-expression:" + context.expression());
        });

        assertThatThrownBy(() -> compile(compiler(expression), """
                <page><if expression-language="demo" test="private-rule">
                    <paragraph>x</paragraph>
                </if></page>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("表达式提供方编译失败")
                .hasMessageNotContaining("private-rule")
                .hasMessageNotContaining("secret-expression");
    }

    /** 验证提供方求值异常不会回显业务值和底层消息。 */
    @Test
    void shouldSanitizeExpressionEvaluationFailure() {
        PrintConditionExpression expression = expression("demo", context -> evaluation -> {
            throw new IllegalStateException(
                    "secret-business:" + evaluation.data().root().path("value").asText());
        });
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><if expression-language="demo" test="rule">
                    <paragraph>x</paragraph>
                </if></page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("value", "private-value"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("表达式求值失败")
                .hasMessageNotContaining("private-value")
                .hasMessageNotContaining("secret-business");
    }

    /** 验证同一表达式编译快照能够并发绑定且上下文不会串扰。 */
    @Test
    void shouldReuseExpressionPlanConcurrently() throws Exception {
        PrintConditionExpression expression = expression("demo", context -> evaluation ->
                evaluation.data().root().path("value").asInt() % 2 == 0);
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><if expression-language="demo" test="even">
                    <paragraph>偶数</paragraph>
                </if></page>
                """);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<Integer>) () -> new XmlTemplateBinder().bind(
                            template, PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                                    .put("value", index))).blocks().size())
                    .toList();

            List<Integer> sizes = executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError("并发表达式绑定失败", exception);
                }
            }).toList();

            assertThat(sizes).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20)
                    .map(index -> index % 2 == 0 ? 1 : 0).boxed().toList());
        } finally {
            executor.shutdownNow();
        }
    }

    /** 创建带一个测试表达式提供方的编译器。 */
    private static XmlTemplateCompiler compiler(PrintConditionExpression expression) {
        return new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of(expression)), new PrintTagRegistry(List.of()));
    }

    /** 创建测试表达式提供方。 */
    private static PrintConditionExpression expression(
            String language,
            java.util.function.Function<ExpressionCompileContext, PrintExpressionPlan> compile) {
        return new PrintConditionExpression() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public PrintExpressionPlan compile(ExpressionCompileContext context) {
                return compile.apply(context);
            }
        };
    }

    /** 编译指定页面内容。 */
    private static CompiledXmlTemplate compile(XmlTemplateCompiler compiler, String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return compiler.compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
