package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.Margins;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.PageOrientation;
import com.github.leyland.letool.print.document.PageSize;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.AnnotationPlacement;
import com.github.leyland.letool.print.document.node.AnnotationType;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用文档模型到受控 XHTML 的稳定映射测试。
 *
 * @author leyland
 */
class PdfXhtmlRendererTest {

    /** 页面尺寸、方向和边距使用已经校验的微米值生成固定 CSS。 */
    @Test
    void shouldRenderLandscapePageLayoutInMillimeters() {
        PageLayout layout = new PageLayout(
                PageSize.A4,
                PageOrientation.LANDSCAPE,
                new Margins(10_000, 20_000, 30_000, 40_000));
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                layout,
                List.of(new ParagraphNode("", List.of(new TextNode("正文")))));

        String xhtml = renderer().render(document);

        assertThat(xhtml)
                .contains("@page { size: 297mm 210mm; margin: 10mm 20mm 30mm 40mm; }")
                .contains("font-family: 'Noto Sans', 'Fallback Sans', sans-serif;");
    }

    /** 文本和属性分别转义，业务内容无法改变 XHTML 结构。 */
    @Test
    void shouldEscapeMetadataAndText() {
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("报告 <一> & \"PDF\"", "作者", "zh-CN"),
                PageLayout.a4Portrait(),
                List.of(new HeadingNode(
                        "heading-one",
                        1,
                        List.of(new TextNode("标题 <script> & \"引号\"")))));

        String xhtml = renderer().render(document);

        assertThat(xhtml)
                .contains("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh-CN\">")
                .contains("<title>报告 &lt;一&gt; &amp; &quot;PDF&quot;</title>")
                .contains("<h1 id=\"heading-one\">标题 &lt;script&gt; &amp; &quot;引号&quot;</h1>")
                .doesNotContain("<script>");
    }

    /** 块节点、表格跨度和导航节点都映射到固定标签。 */
    @Test
    void shouldRenderDocumentStructureAndNavigation() {
        TableNode table = new TableNode("score-table", 1, List.of(
                new TableRow(List.of(cell("表头", 1, 2))),
                new TableRow(List.of(cell("分组", 2, 1), cell("第一行", 1, 1))),
                new TableRow(List.of(cell("第二行", 1, 1)))));
        ParagraphNode navigation = new ParagraphNode("navigation", List.of(
                new BookmarkNode("summary", "汇总"),
                new TextNode(" "),
                new InternalLinkNode("summary", List.of(new TextNode("返回汇总")))));
        SectionNode section = new SectionNode("chapter-one", List.of(
                new HeadingNode("chapter-title", 2, List.of(new TextNode("章节"))),
                navigation,
                table,
                PageBreakNode.INSTANCE));
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(section));

        String xhtml = renderer().render(document);

        assertThat(xhtml)
                .contains("<bookmarks><bookmark name=\"汇总\" href=\"#summary\"></bookmark></bookmarks>")
                .contains("<section id=\"chapter-one\">")
                .contains("<h2 id=\"chapter-title\">章节</h2>")
                .contains("<p id=\"navigation\">"
                        + "<span id=\"summary\" class=\"bookmark\">汇总</span> "
                        + "<a href=\"#summary\">返回汇总</a></p>")
                .contains("<table id=\"score-table\"><thead><tr><th colspan=\"2\"><p>表头</p></th></tr></thead>")
                .contains("<tbody><tr><td rowspan=\"2\"><p>分组</p></td><td><p>第一行</p></td></tr>")
                .contains("<tr><td><p>第二行</p></td></tr></tbody></table>")
                .contains("<div class=\"page-break\"></div>")
                .contains("</section>");
    }

    /** 批注由 PDF 后处理写入，不会成为 XHTML 页面正文。 */
    @Test
    void shouldKeepAnnotationContentOutOfXhtml() {
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new ParagraphNode("summary", List.of(new TextNode("页面正文"))),
                        new AnnotationNode(
                                AnnotationType.TEXT_NOTE,
                                "summary",
                                AnnotationPlacement.TOP_RIGHT,
                                6_000,
                                6_000,
                                0,
                                0,
                                "审核人",
                                "secret-annotation-content")));

        assertThat(renderer().render(document))
                .contains("页面正文")
                .doesNotContain("secret-annotation-content");
    }

    /** 创建带主字体和最终回退字体的映射器。 */
    private PdfXhtmlRenderer renderer() {
        PdfFont primary = new PdfFont(
                "Noto Sans",
                () -> new ByteArrayInputStream(new byte[]{1}),
                false);
        PdfFont fallback = new PdfFont(
                "Fallback Sans",
                () -> new ByteArrayInputStream(new byte[]{2}),
                true);
        return new PdfXhtmlRenderer(List.of(fallback, primary));
    }

    /** 创建只含一个段落的表格单元格。 */
    private TableCell cell(String text, int rowSpan, int colSpan) {
        return new TableCell(
                List.of(new ParagraphNode("", List.of(new TextNode(text)))),
                rowSpan,
                colSpan);
    }
}
