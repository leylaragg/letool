package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.InlineGroupNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证流式文本框使用命名样式和受控动态颜色。 */
class XmlFreeTextBindingTest {

    /** 样式引用和每个框的动态填充色各自保留。 */
    @Test
    void shouldBindFlowFreeTextWithNamedStylesAndDynamicFill() {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                  <styles>
                    <text-style name="note-text" font-size="9pt" line-height="1.4" color="#102030"/>
                    <cell-style name="note-box" padding="1mm" background="#FFFFFF"/>
                  </styles>
                  <page><page-body>
                    <free-text text-style="note-text" box-style="note-box" background-path="note.color"
                        ><field path="note.content"/></free-text>
                  </page-body></page>
                </document>
                """;
        PrintTemplate source = new PrintTemplate("flow-note", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8));
        CompiledXmlTemplate template = new XmlTemplateCompiler().compile(source);
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putObject("note").put("content", "完整内容").put("color", "#C0D0E0");

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(1, data));

        assertThat(XmlTestDocuments.body(document)).hasSize(1);
        assertThat(XmlTestDocuments.body(document).get(0)).isEqualTo(new FreeTextNode(
                "", "note-text", "note-box", "", "完整内容", FreeTextNode.WidthMode.FILL,
                DocumentColor.rgb(192, 208, 224)));
    }

    /** 同一节点可进入段落，动态 ID 和收窄策略不需要宿主手工测量。 */
    @Test
    void shouldBindFitContentInlineWithDynamicId() {
        String body = """
                <paragraph><free-text id-path="id" width="fit-content" background="#102030">字段</free-text></paragraph>
                """;
        ObjectNode data = JsonNodeFactory.instance.objectNode().put("id", "field-box");
        DocumentModel document = bind(body, data);
        ParagraphNode paragraph = (ParagraphNode) XmlTestDocuments.body(document).get(0);
        assertThat(paragraph.children()).containsExactly(new FreeTextNode("field-box", "", "", "", "字段",
                FreeTextNode.WidthMode.FIT_CONTENT, DocumentColor.rgb(16, 32, 48)));
    }

    /** 不受控属性、混合填充源和空正文必须在编译或绑定时明确失败。 */
    @Test
    void shouldRejectInvalidFreeTextDeclarations() {
        for (String body : new String[]{
                "<free-text width=\"shrink\">正文</free-text>",
                "<free-text background=\"#FFFFFF\" background-path=\"color\">正文</free-text>",
                "<free-text background=\"red\">正文</free-text>",
                "<free-text></free-text>",
                "<free-text action=\"javascript:alert(1)\">正文</free-text>",
                "<free-text text-style=\"missing\">正文</free-text>"}) {
            assertThatThrownBy(() -> bind(body, JsonNodeFactory.instance.objectNode()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    /** 模板发布编译阶段就应拒绝缺失样式，不把错误延迟到数据绑定。 */
    @Test
    void shouldRejectMissingStylesBeforeBinding() {
        for (String attribute : List.of("text-style", "box-style")) {
            String xml = """
                    <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                      <page><page-body><free-text %s="missing">正文</free-text></page-body></page>
                    </document>
                    """.formatted(attribute);
            PrintTemplate source = new PrintTemplate("flow-note", TemplateFormat.LETOOL_XML, 1, 1, 1,
                    xml.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(source))
                    .isInstanceOf(PrintCompilationException.class).hasMessageContaining("样式引用");
        }
    }

    /** 自定义标签的完整行内组仍计入文本额度，并遵守循环 ID 限制。 */
    @Test
    void shouldGovernFreeTextInsideCustomInlineGroups() {
        InlineGroupNode group = new InlineGroupNode(List.of(new FreeTextNode("note", "", "", "", "内容")), false)
                .withKeepTogether();
        BindingGovernor governor = new BindingGovernor("flow-note");
        governor.addText(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS - 1L);
        assertThatThrownBy(() -> ExtensionNodeGovernor.govern(group, governor, true))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("文本字符");
        assertThatThrownBy(() -> ExtensionNodeGovernor.govern(group, new BindingGovernor("flow-note"), false))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("ID");
    }

    private static DocumentModel bind(String body, ObjectNode data) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                  <page><page-body>%s</page-body></page>
                </document>
                """.formatted(body);
        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(new PrintTemplate(
                "flow-note", TemplateFormat.LETOOL_XML, 1, 1, 1, xml.getBytes(StandardCharsets.UTF_8)));
        return new XmlTemplateBinder().bind(compiled, PrintContext.of(1, data));
    }
}
