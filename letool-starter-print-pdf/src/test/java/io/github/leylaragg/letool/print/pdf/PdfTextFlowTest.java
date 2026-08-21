package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 段落空白、折行和显式换行映射测试。
 *
 * @author leyland
 */
class PdfTextFlowTest {

    /** 显式换行不依赖空白折叠，业务文本仍按普通文本转义。 */
    @Test
    void shouldRenderExplicitLineBreakAndEscapeLongText() {
        String longText = "A".repeat(8_192) + "<end>";
        DocumentModel document = DocumentModel.singleSequence(
                DocumentMetadata.empty(), PageLayout.a4Portrait(), List.of(
                        new ParagraphNode("", List.of(
                                new TextNode("first"), LineBreakNode.INSTANCE,
                                new TextNode(longText)))));

        String xhtml = renderer().render(document);

        assertThat(xhtml).contains("<p>first<br/>" + "A".repeat(8_192) + "&lt;end&gt;</p>")
                .doesNotContain("<end>");
    }

    /** 保留空白、长词折行和禁止折行分别编译为确定 CSS。 */
    @Test
    void shouldCompileWhitespaceAndWrapModes() {
        StyleSheet styles = StyleSheet.builder()
                .paragraph("preserved", ParagraphStyle.builder()
                        .whitespaceMode(WhitespaceMode.PRESERVE_LINE_BREAKS)
                        .textWrapMode(TextWrapMode.BREAK_LONG_WORDS)
                        .keepTogether(true)
                        .build())
                .paragraph("nowrap", ParagraphStyle.builder()
                        .whitespaceMode(WhitespaceMode.PRESERVE_ALL)
                        .textWrapMode(TextWrapMode.NO_WRAP)
                        .build())
                .build();
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), styles,
                List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                        new ParagraphNode("", "preserved", List.of(new TextNode("a\n b"))),
                        new ParagraphNode("", "nowrap", List.of(new TextNode("c d")))))));

        String xhtml = renderer().render(document);

        assertThat(xhtml)
                .contains("white-space:nowrap;overflow-wrap:normal;page-break-inside:auto")
                .contains("white-space:pre-line;overflow-wrap:break-word;page-break-inside:avoid")
                .contains("<p class=\"lt-paragraph-");
    }

    /** 创建不依赖宿主字体的 XHTML 渲染器。 */
    private PdfXhtmlRenderer renderer() {
        return new PdfXhtmlRenderer(PdfFontCatalog.of(List.of()));
    }
}
