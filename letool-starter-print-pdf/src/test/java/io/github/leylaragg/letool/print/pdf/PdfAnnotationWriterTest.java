package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排版后批注坐标、PDF 类型和稳定外观的真实产物测试。
 *
 * @author leyland
 */
class PdfAnnotationWriterTest {

    /** 批注数量达到上限时仍能通过预检，不会提前拒绝合法文档。 */
    @Test
    void shouldAcceptAnnotationCountAtLimit() {
        List<io.github.leylaragg.letool.print.document.node.BlockNode> blocks = new ArrayList<>();
        blocks.add(new ParagraphNode("summary", List.of(new TextNode("正文"))));
        for (int index = 0; index < PdfAnnotationWriter.MAX_ANNOTATIONS; index++) {
            blocks.add(annotation(
                    AnnotationType.TEXT_NOTE, AnnotationPlacement.TOP_LEFT,
                    6_000, 6_000, "审核人", "批注 " + index));
        }
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(), blocks);

        assertThat(new PdfAnnotationWriter(List.of()).collect(document))
                .hasSize(PdfAnnotationWriter.MAX_ANNOTATIONS);
    }

    /** 两类批注都能被 PDFBox 重新读取，正文只进入批注对象。 */
    @Test
    void shouldWriteTextNoteAndFreeTextAnnotations() throws Exception {
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new ParagraphNode("summary", List.of(new TextNode("页面正文"))),
                        annotation(AnnotationType.TEXT_NOTE, AnnotationPlacement.TOP_RIGHT,
                                6_000, 6_000, "审核人", "便签中文内容"),
                        annotation(AnnotationType.FREE_TEXT, AnnotationPlacement.BOTTOM_LEFT,
                                50_000, 20_000, "复核人", "文本框中文内容")));

        byte[] content = RenderedPdf.render(renderer(), document, RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDPage page = pdf.getPage(0);
            assertThat(page.getAnnotations()).hasSize(2);
            PDAnnotationText note = (PDAnnotationText) page.getAnnotations().get(0);
            PDAnnotationFreeText freeText = (PDAnnotationFreeText) page.getAnnotations().get(1);

            assertAnnotation(note, page, "便签中文内容", "审核人");
            assertThat(note.getName()).isEqualTo(PDAnnotationText.NAME_NOTE);
            assertAnnotation(freeText, page, "文本框中文内容", "复核人");
            assertThat(hasEmbeddedAppearanceFont(freeText)).isTrue();
            for (PDAnnotation annotation : page.getAnnotations()) {
                assertThat(annotation.getCOSObject().containsKey(COSName.A)).isFalse();
                assertThat(annotation.getCOSObject().containsKey(COSName.AA)).isFalse();
                assertThat(annotation.getCOSObject().containsKey(COSName.FS)).isFalse();
            }
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("页面正文")
                    .doesNotContain("便签中文内容", "文本框中文内容");
        }
    }

    /** 跨页目标始终把批注放在目标首个可见页面。 */
    @Test
    void shouldUseFirstVisiblePageOfSpanningTarget() throws Exception {
        List<io.github.leylaragg.letool.print.document.node.BlockNode> children = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            children.add(new ParagraphNode("", List.of(new TextNode("跨页正文 " + index))));
        }
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new SectionNode("long-section", children),
                        new AnnotationNode(
                                AnnotationType.TEXT_NOTE,
                                "long-section",
                                AnnotationPlacement.TOP_LEFT,
                                6_000,
                                6_000,
                                0,
                                0,
                                "审核人",
                                "首段批注")));

        byte[] content = RenderedPdf.render(renderer(), document, RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(content)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(pdf.getPage(0).getAnnotations())
                    .singleElement()
                    .isInstanceOf(PDAnnotationText.class);
            for (int page = 1; page < pdf.getNumberOfPages(); page++) {
                assertThat(pdf.getPage(page).getAnnotations()).isEmpty();
            }
        }
    }

    /** 创建绑定到同一段落的测试批注。 */
    private AnnotationNode annotation(
            AnnotationType type,
            AnnotationPlacement placement,
            int width,
            int height,
            String author,
            String content) {
        return new AnnotationNode(
                type, "summary", placement, width, height,
                0, 0, author, content);
    }

    /** 检查批注元数据、页面范围和普通外观流。 */
    private void assertAnnotation(
            PDAnnotation annotation,
            PDPage page,
            String content,
            String author) {
        assertThat(annotation.getContents()).isEqualTo(content);
        assertThat(((org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup) annotation)
                .getTitlePopup()).isEqualTo(author);
        assertThat(annotation.isPrinted()).isTrue();
        assertThat(annotation.getNormalAppearanceStream()).isNotNull();
        PDRectangle rectangle = annotation.getRectangle();
        PDRectangle cropBox = page.getCropBox();
        assertThat(rectangle.getLowerLeftX()).isGreaterThanOrEqualTo(cropBox.getLowerLeftX());
        assertThat(rectangle.getLowerLeftY()).isGreaterThanOrEqualTo(cropBox.getLowerLeftY());
        assertThat(rectangle.getUpperRightX()).isLessThanOrEqualTo(cropBox.getUpperRightX());
        assertThat(rectangle.getUpperRightY()).isLessThanOrEqualTo(cropBox.getUpperRightY());
    }

    /** 检查自由文本框外观流使用了嵌入字体。 */
    private boolean hasEmbeddedAppearanceFont(PDAnnotationFreeText annotation) throws Exception {
        for (var name : annotation.getNormalAppearanceStream().getResources().getFontNames()) {
            PDFont font = annotation.getNormalAppearanceStream().getResources().getFont(name);
            if (font != null && font.isEmbedded()) {
                return true;
            }
        }
        return false;
    }

    /** 创建使用测试专用字体的 PDF 渲染器。 */
    private PdfDocumentRenderer renderer() {
        return new PdfDocumentRenderer(List.of(
                new PdfFont("Droid Sans Fallback", this::openTestFont, true)));
    }

    /** 每次渲染重新打开测试字体。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }
}
