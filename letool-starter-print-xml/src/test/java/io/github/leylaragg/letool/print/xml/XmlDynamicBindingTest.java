package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 动态字段、条件和循环的真实绑定测试。
 *
 * @author leyland
 */
class XmlDynamicBindingTest {

    /** JSON 测试数据解析器。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 验证标量字段与显式空值按模板顺序生成文本。 */
    @Test
    void shouldBindScalarAndNullFields() throws Exception {
        DocumentModel model = bind("""
                <page><page-body><paragraph>姓名：<field path="policy.name"/>，金额：<field path="policy.amount"/>，有效：<field path="policy.active"/>，备注：<field path="policy.note"/></paragraph></page-body></page>
                """, """
                {"policy":{"name":"张三","amount":12.50,"active":true,"note":null}}
                """);

        assertThat(XmlTestDocuments.body(model)).containsExactly(new ParagraphNode("", List.of(
                new TextNode("姓名："), new TextNode("张三"),
                new TextNode("，金额："), new TextNode("12.5"),
                new TextNode("，有效："), new TextNode("true"),
                new TextNode("，备注："), new TextNode(""))));
    }

    /** 验证动态字段可以独立构成标题内容。 */
    @Test
    void shouldBindFieldOnlyHeading() throws Exception {
        DocumentModel model = bind(
                "<page><page-body><heading><field path=\"title\"/></heading></page-body></page>",
                "{\"title\":\"动态标题\"}");

        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new HeadingNode("", 1, List.of(new TextNode("动态标题"))));
    }

    /** 验证缺失、无法继续遍历和非标量字段均安全失败。 */
    @Test
    void shouldRejectMissingBrokenAndNonScalarFields() throws Exception {
        JsonNode context = JSON.readTree("""
                {"policy":{"name":"张三","none":null,"object":{},"array":[]}}
                """);
        for (String path : List.of(
                "policy.missing", "policy.none.value", "policy.name.value",
                "policy.object", "policy.array")) {
            CompiledXmlTemplate template = compile(
                    "<page><page-body><paragraph><field path=\"" + path + "\"/></paragraph></page-body></page>");

            assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                    template, PrintContext.of(1, context)))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining(path)
                    .hasMessageContaining("行")
                    .hasMessageContaining("列")
                    .hasMessageNotContaining("张三");
        }
    }

    /** 验证结构化条件按字符串、数字、布尔、空值和缺失语义展开块。 */
    @Test
    void shouldBindStructuredConditions() throws Exception {
        DocumentModel model = bind("""
                <page><page-body>
                    <if path="policy.status" operator="eq" value="ACTIVE"><then><paragraph>状态</paragraph></then></if>
                    <if path="policy.amount" operator="gte" value="12.50" value-type="number"><then><paragraph>金额</paragraph></then></if>
                    <if path="policy.active" operator="truthy"><then><paragraph>有效</paragraph></then></if>
                    <if path="policy.note" operator="eq" value-type="null"><then><paragraph>空值</paragraph></then></if>
                    <if path="policy.missing" operator="not-exists"><then><paragraph>缺失</paragraph></then></if>
                    <if path="policy.status" operator="ne" value="ACTIVE"><then><paragraph>不应出现</paragraph></then></if>
                </page-body></page>
                """, """
                {"policy":{"status":"ACTIVE","amount":12.5,"active":true,"note":null}}
                """);

        assertThat(XmlTestDocuments.body(model)).containsExactly(
                paragraph("状态"), paragraph("金额"), paragraph("有效"),
                paragraph("空值"), paragraph("缺失"));
    }

    /** 验证结构化条件的全部首批操作符。 */
    @Test
    void shouldSupportAllStructuredConditionOperators() throws Exception {
        DocumentModel model = bind("""
                <page><page-body>
                    <if path="present" operator="exists"><then><paragraph>exists</paragraph></then></if>
                    <if path="missing" operator="not-exists"><then><paragraph>not-exists</paragraph></then></if>
                    <if path="yes" operator="truthy"><then><paragraph>truthy</paragraph></then></if>
                    <if path="no" operator="falsy"><then><paragraph>falsy</paragraph></then></if>
                    <if path="yes" operator="eq" value="true" value-type="boolean"><then><paragraph>eq</paragraph></then></if>
                    <if path="yes" operator="ne" value="false" value-type="boolean"><then><paragraph>ne</paragraph></then></if>
                    <if path="amount" operator="gt" value="9" value-type="number"><then><paragraph>gt</paragraph></then></if>
                    <if path="amount" operator="gte" value="10" value-type="number"><then><paragraph>gte</paragraph></then></if>
                    <if path="amount" operator="lt" value="11" value-type="number"><then><paragraph>lt</paragraph></then></if>
                    <if path="amount" operator="lte" value="10" value-type="number"><then><paragraph>lte</paragraph></then></if>
                </page-body></page>
                """, "{\"present\":null,\"yes\":true,\"no\":false,\"amount\":10}");

        assertThat(XmlTestDocuments.body(model)).extracting(Object::toString)
                .allSatisfy(value -> assertThat(value).doesNotContain("missing"));
        assertThat(XmlTestDocuments.body(model)).hasSize(10);
    }

    /** 验证存在性判断不会把无法继续遍历的路径误判为字段缺失。 */
    @Test
    void shouldRejectBrokenPathForExistenceCondition() throws Exception {
        CompiledXmlTemplate template = compile("""
                <page><page-body><if path="policy.name.value" operator="not-exists"><then><paragraph>正文</paragraph></then></if></page-body></page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JSON.readTree("{\"policy\":{\"name\":\"敏感值\"}}"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract")
                .hasMessageContaining("policy.name.value")
                .hasMessageContaining("无法继续遍历")
                .hasMessageNotContaining("敏感值");
    }

    /** 验证动态内容为空时剪枝内部章节，而不是生成非法空章节。 */
    @Test
    void shouldPruneSectionsEmptiedByDynamicBinding() throws Exception {
        DocumentModel model = bind("""
                <page><page-body>
                    <section><if path="active" operator="truthy"><then><paragraph>不应生成</paragraph></then></if></section>
                    <section><for-each items="empty" var="item"><paragraph>不应生成</paragraph></for-each></section>
                    <section><for-each items="none" var="item"><paragraph>不应生成</paragraph></for-each></section>
                    <paragraph>保留</paragraph>
                </page-body></page>
                """, "{\"active\":false,\"empty\":[],\"none\":null}");

        assertThat(XmlTestDocuments.body(model)).containsExactly(paragraph("保留"));
    }

    /** 验证字段和循环来源会区分缺失路径与无法继续遍历。 */
    @Test
    void shouldReportBrokenFieldAndLoopPaths() throws Exception {
        JsonNode context = JSON.readTree("{\"policy\":{\"name\":\"敏感值\"}}");
        List<String> pages = List.of(
                "<page><page-body><paragraph><field path=\"policy.name.value\"/></paragraph></page-body></page>",
                "<page><page-body><for-each items=\"policy.name.value\" var=\"item\"><paragraph>正文</paragraph></for-each></page-body></page>");

        for (String page : pages) {
            assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                    compile(page), PrintContext.of(1, context)))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("policy.name.value")
                    .hasMessageContaining("无法继续遍历")
                    .hasMessageNotContaining("敏感值");
        }
    }

    /** 验证非法条件属性组合在编译期失败。 */
    @Test
    void shouldRejectInvalidConditionDeclarations() {
        List<String> declarations = List.of(
                "path=\"policy.active\" operator=\"truthy\" value=\"true\"",
                "path=\"policy.amount\" operator=\"gt\" value=\"1\"",
                "path=\"policy.active\" operator=\"eq\" value=\"yes\" value-type=\"boolean\"",
                "path=\"policy.note\" operator=\"eq\" value=\"x\" value-type=\"null\"",
                "path=\"policy.status\" operator=\"contains\" value=\"A\"");

        for (String declaration : declarations) {
            assertThatThrownBy(() -> compile(
                    "<page><page-body><if " + declaration + "><then><paragraph>正文</paragraph></then></if></page-body></page>"))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证条件类型不匹配不会被静默当作不成立。 */
    @Test
    void shouldRejectConditionTypeMismatch() throws Exception {
        CompiledXmlTemplate template = compile("""
                <page><page-body><if path="policy.amount" operator="eq" value="12.5"><then><paragraph>正文</paragraph></then></if></page-body></page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JSON.readTree("{\"policy\":{\"amount\":12.5}}"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("类型")
                .hasMessageNotContaining("12.5");
    }

    /** 验证循环按数组顺序展开对象、标量和显式空数组。 */
    @Test
    void shouldBindObjectAndScalarLoops() throws Exception {
        DocumentModel model = bind("""
                <page><page-body>
                    <for-each items="policy.names" var="name">
                        <paragraph><field path="$name"/></paragraph>
                    </for-each>
                    <for-each items="policy.coverages" var="coverage">
                        <paragraph><field path="$coverage.name"/></paragraph>
                    </for-each>
                    <for-each items="policy.none" var="unused">
                        <paragraph>不应出现</paragraph>
                    </for-each>
                </page-body></page>
                """, """
                {"policy":{"names":["甲","乙"],"coverages":[{"name":"寿险"},{"name":"医疗险"}],"none":null}}
                """);

        assertThat(XmlTestDocuments.body(model)).containsExactly(
                paragraph("甲"), paragraph("乙"), paragraph("寿险"), paragraph("医疗险"));
    }

    /** 验证嵌套循环可以读取外层变量并保持词法作用域。 */
    @Test
    void shouldBindNestedLexicalScopes() throws Exception {
        DocumentModel model = bind("""
                <page><page-body>
                    <for-each items="groups" var="group">
                        <for-each items="$group.items" var="item">
                            <paragraph><field path="$group.name"/>：<field path="$item"/></paragraph>
                        </for-each>
                    </for-each>
                </page-body></page>
                """, """
                {"groups":[{"name":"A","items":[1,2]},{"name":"B","items":[3]}]}
                """);

        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("A"), new TextNode("："), new TextNode("1"))),
                new ParagraphNode("", List.of(new TextNode("A"), new TextNode("："), new TextNode("2"))),
                new ParagraphNode("", List.of(new TextNode("B"), new TextNode("："), new TextNode("3"))));
    }

    /** 验证循环来源缺失或不是数组时绑定失败。 */
    @Test
    void shouldRejectInvalidLoopSources() throws Exception {
        JsonNode context = JSON.readTree("{\"policy\":{\"name\":\"秘密值\"}}");
        for (String path : List.of("policy.missing", "policy.name")) {
            CompiledXmlTemplate template = compile("""
                    <page><page-body><for-each items="%s" var="item"><paragraph>正文</paragraph></for-each></page-body></page>
                    """.formatted(path));

            assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, context)))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining(path)
                    .hasMessageNotContaining("秘密值");
        }
    }

    /** 验证变量重名、越界引用和循环后代 ID 在编译期失败。 */
    @Test
    void shouldRejectInvalidLoopScopesAndIds() {
        List<String> pages = List.of(
                "<page><page-body><for-each items=\"groups\" var=\"item\"><for-each items=\"$item.children\" var=\"item\"><paragraph>正文</paragraph></for-each></for-each></page-body></page>",
                "<page><page-body><paragraph><field path=\"$item.name\"/></paragraph></page-body></page>",
                "<page><page-body><for-each items=\"groups\" var=\"item\"><paragraph id=\"dynamic\">正文</paragraph></for-each></page-body></page>");

        for (String page : pages) {
            assertThatThrownBy(() -> compile(page))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证同一不可变编译快照可被并发绑定且不会串扰上下文。 */
    @Test
    void shouldReuseCompiledTemplateConcurrently() throws Exception {
        CompiledXmlTemplate template = compile(
                "<page><page-body><paragraph><field path=\"name\"/></paragraph></page-body></page>");
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<String>) () -> {
                        ObjectNode root = JsonNodeFactory.instance.objectNode()
                                .put("name", "name-" + index);
                        DocumentModel model = new XmlTemplateBinder().bind(
                                template, PrintContext.of(1, root));
                        ParagraphNode paragraph = (ParagraphNode) XmlTestDocuments.body(model).get(0);
                        return ((TextNode) paragraph.children().get(0)).text();
                    })
                    .toList();

            List<String> values = executor.invokeAll(tasks).stream()
                    .map(XmlDynamicBindingTest::completedValue)
                    .toList();

            assertThat(values).containsExactlyElementsOf(
                    java.util.stream.IntStream.range(0, 20)
                            .mapToObj(index -> "name-" + index)
                            .toList());
        } finally {
            executor.shutdownNow();
        }
    }

    /** 读取已经完成的并发绑定结果。 */
    private static String completedValue(Future<String> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("并发绑定失败", exception);
        }
    }

    /** 创建仅含一个文本节点的段落。 */
    private ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }

    /** 绑定页面与 JSON 上下文。 */
    private DocumentModel bind(String page, String json) throws Exception {
        return new XmlTemplateBinder().bind(
                compile(page), PrintContext.of(1, JSON.readTree(json)));
    }

    /** 编译指定页面内容。 */
    private CompiledXmlTemplate compile(String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
