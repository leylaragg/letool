package com.github.leyland.letool.print.spel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.xml.CompiledXmlTemplate;
import com.github.leyland.letool.print.xml.PrintCompilationException;
import com.github.leyland.letool.print.xml.XmlTemplateBinder;
import com.github.leyland.letool.print.xml.XmlTemplateCompiler;
import com.github.leyland.letool.print.xml.expression.PrintExpressionRegistry;
import com.github.leyland.letool.print.xml.format.BuiltInPrintFormatters;
import com.github.leyland.letool.print.xml.tag.PrintTagRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受限 SpEL 通过真实 XML 编译和绑定入口执行的集成测试。
 *
 * @author leyland
 */
class RestrictedSpelXmlIntegrationTest {

    /** 测试使用的 JSON 解析器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证模块进入类路径但没有显式注册时，SpEL 仍在模板编译期失败。
     */
    @Test
    void shouldRequireExplicitProviderRegistration() {
        String page = """
                <page><if expression-language="spel" test="enabled == true">
                    <paragraph>显示</paragraph>
                </if></page>
                """;

        assertThatThrownBy(() -> compile(new XmlTemplateCompiler(), page))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("条件表达式语言未注册");

        assertThat(compile(spelCompiler(), page)).isNotNull();
    }

    /**
     * 验证根属性、数组读取、布尔组合和空值判断能够控制真实文档节点生成。
     */
    @Test
    void shouldBindRootArrayBooleanAndNullConditions() throws Exception {
        CompiledXmlTemplate template = compile(spelCompiler(), """
                <page>
                    <if expression-language="spel"
                        test="status == 'ACTIVE' &amp;&amp; items[0].enabled == true">
                        <paragraph>启用</paragraph>
                    </if>
                    <if expression-language="spel" test="remark == null">
                        <paragraph>无备注</paragraph>
                    </if>
                </page>
                """);

        DocumentModel model = bind(template, """
                {"status":"ACTIVE","items":[{"enabled":true}],"remark":null}
                """);

        assertThat(model.blocks()).containsExactly(
                paragraph("启用"), paragraph("无备注"));
    }

    /**
     * 验证循环变量可见，并优先遮蔽根对象中的同名字段。
     */
    @Test
    void shouldReadLoopVariableWithLexicalShadowing() throws Exception {
        CompiledXmlTemplate template = compile(spelCompiler(), """
                <page><for-each items="items" var="item">
                    <if expression-language="spel" test="item.visible == true">
                        <paragraph><field path="$item.name"/></paragraph>
                    </if>
                </for-each></page>
                """);

        DocumentModel model = bind(template, """
                {
                  "item":{"name":"根对象","visible":true},
                  "items":[
                    {"name":"A","visible":true},
                    {"name":"B","visible":false}
                  ]
                }
                """);

        assertThat(model.blocks()).containsExactly(paragraph("A"));
    }

    /**
     * 验证结构化条件继续可用，且不能与 SpEL 条件属性混用。
     */
    @Test
    void shouldPreserveStructuredConditionsAndRejectMixedDeclaration() throws Exception {
        CompiledXmlTemplate structured = compile(new XmlTemplateCompiler(), """
                <page><if path="enabled" operator="truthy">
                    <paragraph>结构化</paragraph>
                </if></page>
                """);

        assertThat(bind(structured, "{\"enabled\":true}").blocks())
                .containsExactly(paragraph("结构化"));
        assertThatThrownBy(() -> compile(spelCompiler(), """
                <page><if expression-language="spel" test="enabled == true"
                    path="enabled" operator="truthy">
                    <paragraph>非法</paragraph>
                </if></page>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("不能与结构化条件属性混用");
        assertThatThrownBy(() -> compile(spelCompiler(), """
                <page><for-each items="groups" var="item">
                    <for-each items="$item.children" var="item">
                        <if expression-language="spel" test="item.enabled == true">
                            <paragraph>非法</paragraph>
                        </if>
                    </for-each>
                </for-each></page>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("循环变量不能与外层变量重名");
    }

    /**
     * 验证同一编译模板可以反复绑定不同上下文。
     */
    @Test
    void shouldReuseCompiledTemplateAcrossDifferentContexts() throws Exception {
        CompiledXmlTemplate template = compile(spelCompiler(), """
                <page><if expression-language="spel" test="enabled == true">
                    <paragraph>显示</paragraph>
                </if></page>
                """);

        assertThat(bind(template, "{\"enabled\":true}").blocks()).hasSize(1);
        assertThat(bind(template, "{\"enabled\":false}").blocks()).isEmpty();
    }

    /**
     * 验证同一编译模板并发绑定时不共享数据根、变量或预算状态。
     */
    @Test
    void shouldReuseCompiledTemplateConcurrently() throws Exception {
        CompiledXmlTemplate template = compile(spelCompiler(), """
                <page><if expression-language="spel" test="enabled == true">
                    <paragraph>显示</paragraph>
                </if></page>
                """);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, 24)
                    .mapToObj(index -> (Callable<Integer>) () -> bind(
                            template, "{\"enabled\":" + (index % 2 == 0) + "}")
                            .blocks().size())
                    .toList();

            List<Integer> sizes = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError("并发 SpEL 绑定失败", exception);
                        }
                    })
                    .toList();

            assertThat(sizes).containsExactlyElementsOf(
                    java.util.stream.IntStream.range(0, 24)
                            .map(index -> index % 2 == 0 ? 1 : 0)
                            .boxed()
                            .toList());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证 XML 层转换编译和求值失败时不回显模板正文与业务值。
     */
    @Test
    void shouldSanitizeCompilationAndEvaluationFailures() throws Exception {
        String secret = "secret-business-value";
        assertThatThrownBy(() -> compile(spelCompiler(), """
                <page><if expression-language="spel"
                    test="T(java.lang.Runtime).getRuntime().exec('%s') != null">
                    <paragraph>非法</paragraph>
                </if></page>
                """.formatted(secret)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("java.lang.Runtime");

        CompiledXmlTemplate template = compile(spelCompiler(), """
                <page><if expression-language="spel"
                    test="missing == '%s'">
                    <paragraph>非法</paragraph>
                </if></page>
                """.formatted(secret));
        assertThatThrownBy(() -> bind(template, "{\"value\":\"" + secret + "\"}"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("missing")
                .hasMessageNotContaining("RestrictedSpelDataNode");
    }

    /**
     * 创建只注册受限 SpEL 的 XML 编译器。
     *
     * @return 使用内置格式化器和受限 SpEL 的编译器
     */
    private XmlTemplateCompiler spelCompiler() {
        return new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of(
                        new RestrictedSpelConditionExpression())),
                new PrintTagRegistry(List.of()));
    }

    /**
     * 编译指定 XML 页面片段。
     *
     * @param compiler XML 模板编译器
     * @param page 页面正文
     * @return 编译后的不可变模板
     */
    private CompiledXmlTemplate compile(
            XmlTemplateCompiler compiler, String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return compiler.compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 使用 JSON 根对象绑定已编译模板。
     *
     * @param template 已编译模板
     * @param json JSON 根对象正文
     * @return 通用文档模型
     * @throws Exception JSON 解析失败时抛出
     */
    private DocumentModel bind(
            CompiledXmlTemplate template, String json) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return new XmlTemplateBinder().bind(
                template, PrintContext.of(1, root));
    }

    /**
     * 创建测试期望使用的段落节点。
     *
     * @param text 段落文本
     * @return 只包含一个文本节点的段落
     */
    private ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }
}
