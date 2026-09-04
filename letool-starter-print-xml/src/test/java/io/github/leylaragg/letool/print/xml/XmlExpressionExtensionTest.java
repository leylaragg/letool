package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.ExpressionEvaluationContext;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * XML 条件表达式扩展的编译和绑定契约测试。
 *
 * @author leyland
 */
class XmlExpressionExtensionTest {

    /** 验证显式注册的表达式计划能够读取根数据并控制块展开。 */
    @Test
    void shouldCompileAndEvaluateRegisteredExpression() {
        PrintConditionExpression expression = expression("demo", context -> plan(
                context.expression(), evaluation -> evaluation.data().root()
                        .path(context.expression()).asBoolean()));
        XmlTemplateCompiler compiler = compiler(expression);
        CompiledXmlTemplate template = compile(compiler, """
                <page><page-body>
                    <if expression-language="demo" test="enabled"><then>
                        <paragraph>显示</paragraph>
                    </then></if>
                </page-body></page>
                """);

        DocumentModel shown = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("enabled", true)));
        DocumentModel hidden = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("enabled", false)));

        assertThat(XmlTestDocuments.body(shown)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("显示"))));
        assertThat(XmlTestDocuments.body(hidden)).isEmpty();
    }

    /** 验证循环变量通过只读数据视图提供给表达式计划。 */
    @Test
    void shouldExposeLoopVariablesThroughReadOnlyView() throws Exception {
        PrintConditionExpression expression = expression("demo", context -> plan(
                "$item." + context.expression(), evaluation -> evaluation.data()
                        .variable("item").orElseThrow().path(context.expression()).asBoolean()));
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><page-body><for-each items="items" var="item">
                    <if expression-language="demo" test="visible"><then>
                        <paragraph><field path="$item.name"/></paragraph>
                    </then></if>
                </for-each></page-body></page>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(template, PrintContext.of(1,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"items":[{"name":"A","visible":true},{"name":"B","visible":false}]}
                        """)));

        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("A"))));
    }

    /** 验证未注册、缺失配对、结构化混用和空表达式在编译期失败。 */
    @Test
    void shouldRejectInvalidExpressionDeclarations() {
        XmlTemplateCompiler empty = new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
        List<String> pages = List.of(
                "<page><page-body><if expression-language=\"missing\" test=\"enabled\"><then><paragraph>x</paragraph></then></if></page-body></page>",
                "<page><page-body><if expression-language=\"demo\"><then><paragraph>x</paragraph></then></if></page-body></page>",
                "<page><page-body><if test=\"enabled\"><then><paragraph>x</paragraph></then></if></page-body></page>",
                "<page><page-body><if expression-language=\"demo\" test=\"enabled\" path=\"enabled\"><then><paragraph>x</paragraph></then></if></page-body></page>",
                "<page><page-body><if expression-language=\"demo\" test=\"\"><then><paragraph>x</paragraph></then></if></page-body></page>",
                "<page><page-body><if expression-language=\"demo\" test=\""
                        + "x".repeat(XmlDsl.MAX_EXPRESSION_CHARACTERS + 1)
                        + "\"><then><paragraph>x</paragraph></then></if></page-body></page>");

        for (String page : pages) {
            XmlTemplateCompiler compiler = page.contains("missing") ? empty : compiler(
                    expression("demo", context -> plan(evaluation -> true)));
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
                <page><page-body><if expression-language="demo" test="private-rule"><then>
                    <paragraph>x</paragraph>
                </then></if></page-body></page>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("表达式提供方编译失败")
                .hasMessageNotContaining("private-rule")
                .hasMessageNotContaining("secret-expression")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    /** 验证提供方求值异常不会回显业务值和底层消息。 */
    @Test
    void shouldSanitizeExpressionEvaluationFailure() {
        IllegalStateException cause = new IllegalStateException("secret-business:private-value");
        PrintConditionExpression expression = expression("demo", context -> plan(
                "value", evaluation -> {
                    throw cause;
                }));
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><page-body><if expression-language="demo" test="rule"><then>
                    <paragraph>x</paragraph>
                </then></if></page-body></page>
                """);

        Throwable thrown = catchThrowable(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(
                        1, JsonNodeFactory.instance.objectNode().put("value", "private-value"))));

        assertThat(thrown)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract：/document/page[1]/page-body/if，")
                .hasMessageContaining("表达式求值失败")
                .hasMessageNotContaining("private-value")
                .hasMessageNotContaining("secret-business");
        assertThat(thrown.getCause()).isSameAs(cause);
    }

    /** 验证同一表达式编译快照能够并发绑定且上下文不会串扰。 */
    @Test
    void shouldReuseExpressionPlanConcurrently() throws Exception {
        PrintConditionExpression expression = expression("demo", context -> plan(
                "value", evaluation -> evaluation.data().root().path("value").asInt() % 2 == 0));
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><page-body><if expression-language="demo" test="even"><then>
                    <paragraph>偶数</paragraph>
                </then></if></page-body></page>
                """);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<Integer>) () -> {
                        DocumentModel model = new XmlTemplateBinder().bind(
                                template, PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                                        .put("value", index)));
                        return XmlTestDocuments.body(model).size();
                    })
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

    /** 编译结果只采用首次校验过的表达式贡献，不再回调有状态扩展。 */
    @Test
    void shouldFreezeExpressionInspectionContribution() {
        AtomicInteger contributionReads = new AtomicInteger();
        TemplateInspectionContribution contribution = TemplateInspectionContribution.builder()
                .dataPath("enabled").build();
        PrintConditionExpression expression = expression("demo", context ->
                new PrintExpressionPlan() {
                    @Override
                    public boolean evaluate(ExpressionEvaluationContext evaluation) {
                        return evaluation.data().root().path("enabled").asBoolean();
                    }

                    @Override
                    public TemplateInspectionContribution inspectionContribution() {
                        if (contributionReads.incrementAndGet() > 1) {
                            throw new IllegalStateException("贡献不能被重复读取");
                        }
                        return contribution;
                    }
                });
        CompiledXmlTemplate template = compile(compiler(expression), """
                <page><page-body><if expression-language="demo" test="rule"><then>
                    <paragraph>显示</paragraph>
                </then></if></page-body></page>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("enabled", true)));

        assertThat(template.inspection().pathUsages())
                .extracting(usage -> usage.dataPath())
                .contains("enabled");
        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("显示"))));
        assertThat(contributionReads).hasValue(1);
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

    /**
     * 创建明确声明“不读取数据”的测试表达式计划。
     *
     * @param evaluator 测试求值函数
     * @return 可复用测试计划
     */
    private static PrintExpressionPlan plan(Predicate<ExpressionEvaluationContext> evaluator) {
        return PrintExpressionPlan.of(TemplateInspectionContribution.empty(), evaluator);
    }

    /**
     * 创建声明单条数据读取路径的测试表达式计划。
     *
     * @param dataPath 求值函数读取的数据路径
     * @param evaluator 测试求值函数
     * @return 可复用测试计划
     */
    private static PrintExpressionPlan plan(
            String dataPath, Predicate<ExpressionEvaluationContext> evaluator) {
        return PrintExpressionPlan.of(TemplateInspectionContribution.builder()
                .dataPath(dataPath).build(), evaluator);
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
