package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新页面结构和行内文本顺序的编译测试。
 *
 * @author leyland
 */
class XmlDocumentStructureTest {

    /** 页眉、正文和页脚应各自绑定，XML 结构缩进不能混入正文。 */
    @Test
    void shouldBindOrderedPageRegionsAndInlineText() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>
                        <page-header>
                            <paragraph>页眉</paragraph>
                        </page-header>
                        <page-body>
                            <paragraph>甲 <field path="name"/><line-break/> 乙</paragraph>
                        </page-body>
                        <page-footer>
                            <paragraph>页脚</paragraph>
                        </page-footer>
                    </page>
                </document>
                """);

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("name", "中")));

        assertThat(document.pageSequences()).hasSize(1);
        assertThat(document.pageSequences().get(0).header().blocks())
                .containsExactly(new ParagraphNode("", java.util.List.of(new TextNode("页眉"))));
        assertThat(document.pageSequences().get(0).body()).containsExactly(
                new ParagraphNode("", java.util.List.of(
                        new TextNode("甲 "), new TextNode("中"),
                        LineBreakNode.INSTANCE, new TextNode(" 乙"))));
        assertThat(document.pageSequences().get(0).footer().blocks())
                .containsExactly(new ParagraphNode("", java.util.List.of(new TextNode("页脚"))));
    }

    /** 旧页面正文和错误区域顺序不能进入新的标准编译树。 */
    @Test
    void shouldRejectLegacyOrOutOfOrderPageContent() {
        assertThatThrownBy(() -> compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>旧正文</paragraph></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>
                        <page-footer><paragraph>页脚</paragraph></page-footer>
                        <page-body/>
                    </page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 每个页面都必须有且只有一个正文区域。 */
    @Test
    void shouldRequireOnePageBody() {
        assertThatThrownBy(() -> compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-header/></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body/><page-body/></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 编译一个使用新标准结构的文档。 */
    private CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 7, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
