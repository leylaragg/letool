package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 行内文本和空白处理声明测试。
 *
 * @author leyland
 */
class XmlTextContentTest {

    /** 行内空格和显式换行应保持模板中的先后顺序。 */
    @Test
    void shouldKeepInlineTextAndExplicitLineBreakInOrder() {
        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body><paragraph>甲 <text>乙</text><line-break/> 丙</paragraph></page-body></page>
                </document>
                """);

        assertThat(((ParagraphNode) document.pageSequences().get(0).body().get(0)).children())
                .containsExactly(new TextNode("甲 "), new TextNode("乙"),
                        LineBreakNode.INSTANCE, new TextNode(" 丙"));
    }

    /** 三种空白模式和折行模式都应编译为类型化样式，而不是交给渲染器猜测字符串。 */
    @Test
    void shouldCompileEveryWhitespaceAndWrapMode() {
        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <styles>
                        <paragraph-style name="normal" whitespace="collapse" wrap="normal"/>
                        <paragraph-style name="lines" whitespace="preserve-line-breaks" wrap="break-long-words"/>
                        <paragraph-style name="literal" whitespace="preserve-all" wrap="no-wrap"/>
                    </styles>
                    <page><page-body><paragraph style="normal">正文</paragraph></page-body></page>
                </document>
                """);

        assertThat(List.of("normal", "lines", "literal")).allSatisfy(name ->
                assertThat(document.styleSheet().paragraph(name)).isPresent());
        assertThat(document.styleSheet().paragraph("normal").orElseThrow().whitespaceMode())
                .isEqualTo(WhitespaceMode.COLLAPSE);
        assertThat(document.styleSheet().paragraph("lines").orElseThrow().whitespaceMode())
                .isEqualTo(WhitespaceMode.PRESERVE_LINE_BREAKS);
        assertThat(document.styleSheet().paragraph("literal").orElseThrow().whitespaceMode())
                .isEqualTo(WhitespaceMode.PRESERVE_ALL);
        assertThat(document.styleSheet().paragraph("normal").orElseThrow().textWrapMode())
                .isEqualTo(TextWrapMode.NORMAL);
        assertThat(document.styleSheet().paragraph("lines").orElseThrow().textWrapMode())
                .isEqualTo(TextWrapMode.BREAK_LONG_WORDS);
        assertThat(document.styleSheet().paragraph("literal").orElseThrow().textWrapMode())
                .isEqualTo(TextWrapMode.NO_WRAP);
    }

    /** line-break 只属于行内内容，块级区域不能直接接收。 */
    @Test
    void shouldRejectLineBreakOutsideInlineContainer() {
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body><line-break/></page-body></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 编译并绑定一个无需业务数据的文档。 */
    private DocumentModel bind(String xml) {
        CompiledXmlTemplate template = new XmlTemplateCompiler().compile(new PrintTemplate(
                "document-main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
        return new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode()));
    }
}
