package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.pdf.PdfDocumentRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证同一文档模型在 PDF 和 DOCX 中保留共同的结构语义。
 *
 * @author leyland
 */
class DocxCrossFormatStructureTest {

    /** 跨格式只比较内容顺序和导航标签，不比较分页或换行坐标。 */
    @Test
    void shouldPreserveSharedDocumentStructureAcrossFormats() throws Exception {
        DocumentModel document = sharedDocument();
        byte[] docx = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults()).content();
        byte[] pdf = new PdfDocumentRenderer(List.of())
                .render(document, RenderOptions.defaults()).content();

        WordprocessingMLPackage reopenedDocx = WordprocessingMLPackage.load(
                new ByteArrayInputStream(docx));
        String docxXml = XmlUtils.marshaltoString(
                reopenedDocx.getMainDocumentPart().getJaxbElement(), true, true);
        String pdfText;
        try (var reopenedPdf = Loader.loadPDF(pdf)) {
            pdfText = new PDFTextStripper().getText(reopenedPdf);
        }

        assertInOrder(docxXml, "Shared title", "Shared body", "Cell A", "Cell B", "Back");
        assertInOrder(pdfText, "Shared title", "Shared body", "Cell A", "Cell B", "Back");
    }

    /** 创建不依赖具体输出能力的共享结构样本。 */
    private static DocumentModel sharedDocument() {
        TableNode table = new TableNode("", 0, List.of(new TableRow(List.of(
                cell("Cell A"), cell("Cell B")))));
        return new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("title", 1, List.of(new TextNode("Shared title"))),
                        new ParagraphNode("", List.of(new TextNode("Shared body"))),
                        table,
                        new ParagraphNode("", List.of(new InternalLinkNode(
                                "title", List.of(new TextNode("Back")))))));
    }

    /** 创建普通单元格。 */
    private static TableCell cell(String text) {
        return new TableCell(List.of(
                new ParagraphNode("", List.of(new TextNode(text)))), 1, 1);
    }

    /** 断言各段文字按模型声明顺序出现。 */
    private static void assertInOrder(String content, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = content.indexOf(value);
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }
}
