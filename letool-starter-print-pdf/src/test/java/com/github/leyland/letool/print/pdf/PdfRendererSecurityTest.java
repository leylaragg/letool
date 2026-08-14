package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.AnnotationPlacement;
import com.github.leyland.letool.print.document.node.AnnotationType;
import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintRenderingException;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 渲染器的资源、容量和异常脱敏边界测试。
 *
 * @author leyland
 */
class PdfRendererSecurityTest {

    /** 页数在真正写出 PDF 前完成检查。 */
    @Test
    void shouldRejectDocumentBeyondPageLimit() {
        DocumentModel document = document(List.of(
                paragraph("第一页 secret-page-content"),
                PageBreakNode.INSTANCE,
                paragraph("第二页")));

        assertThatThrownBy(() -> renderer().render(
                document,
                new RenderOptions(1, 10L * 1024 * 1024, true)))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_011")
                .hasMessageNotContaining("secret-page-content");
    }

    /** 产物写入超过上限时使用容量错误码，并保留内部原因链。 */
    @Test
    void shouldStopWritingBeyondOutputLimit() {
        DocumentModel document = document(List.of(paragraph(largeRandomText(1_600_000))));

        assertThatThrownBy(() -> renderer().render(
                document,
                new RenderOptions(20_000, 1024L * 1024, true)))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_007")
                .hasCauseInstanceOf(Exception.class);
    }

    /** 字体供应器的路径或实现消息不会进入用户可见异常。 */
    @Test
    void shouldHideFontSupplierFailure() {
        PdfFont brokenFont = new PdfFont("Broken Font", () -> {
            throw new IllegalStateException("secret-font-path");
        }, true);
        PdfDocumentRenderer renderer = new PdfDocumentRenderer(List.of(brokenFont));

        assertThatThrownBy(() -> renderer.render(
                document(List.of(paragraph("正文"))),
                RenderOptions.defaults()))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_010")
                .hasMessageNotContaining("secret-font-path");
    }

    /** 图片资源尚未进入 PDF 能力集合，资源 ID 不会触发任何加载。 */
    @Test
    void shouldRejectImageNodeBeforeRendering() {
        ImageNode image = new ImageNode(
                "logo",
                "file:///C:/secret-image.png",
                "logo",
                20_000,
                20_000);

        assertThatThrownBy(() -> renderer().render(
                document(List.of(image)),
                RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("ImageNode")
                .hasMessageNotContaining("secret-image.png");
    }

    /** 看似 XML 或 URL 的业务文本只能作为可提取正文进入 PDF。 */
    @Test
    void shouldKeepMarkupAndUrlAsPlainText() throws Exception {
        String text = "外部内容 <img src=\"http://127.0.0.1/secret\"/> file:///C:/secret";

        byte[] content = renderer().render(
                document(List.of(paragraph(text))),
                RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(content)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains(text);
        }
    }

    /** 批注数量在排版前受中央 PDF 容量限制。 */
    @Test
    void shouldRejectTooManyAnnotationsBeforeLayout() {
        List<BlockNode> blocks = new java.util.ArrayList<>();
        blocks.add(new ParagraphNode("summary", List.of(new TextNode("正文"))));
        for (int index = 0; index <= PdfAnnotationWriter.MAX_ANNOTATIONS; index++) {
            blocks.add(annotation(
                    AnnotationType.TEXT_NOTE, 6_000, 6_000,
                    0, 0, "secret-annotation-content"));
        }

        assertThatThrownBy(() -> renderer().render(document(blocks), RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("批注数量")
                .hasMessageNotContaining("secret-annotation-content");
    }

    /** 最终矩形越出页面时拒绝产物，不替模板静默改写位置。 */
    @Test
    void shouldRejectAnnotationOutsidePage() {
        DocumentModel document = document(List.of(
                new ParagraphNode("summary", List.of(new TextNode("正文"))),
                annotation(AnnotationType.TEXT_NOTE, 6_000, 6_000,
                        -300_000, 0, "secret-position-content")));

        assertThatThrownBy(() -> renderer().render(document, RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("超出页面")
                .hasMessageNotContaining("secret-position-content");
    }

    /** 自由文本框没有宿主字体时安全失败，避免中文外观静默丢字。 */
    @Test
    void shouldRequireHostFontForFreeTextAnnotation() {
        DocumentModel document = document(List.of(
                new ParagraphNode("summary", List.of(new TextNode("body"))),
                annotation(AnnotationType.FREE_TEXT, 50_000, 20_000,
                        0, 0, "secret-free-text")));

        assertThatThrownBy(() -> new PdfDocumentRenderer(List.of())
                .render(document, RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("宿主字体")
                .hasMessageNotContaining("secret-free-text");
    }

    /** 创建普通测试文档。 */
    private DocumentModel document(List<? extends BlockNode> blocks) {
        return new DocumentModel(DocumentMetadata.empty(), PageLayout.a4Portrait(), List.copyOf(blocks));
    }

    /** 创建单段正文。 */
    private ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }

    /** 创建绑定到 summary 段落的测试批注。 */
    private AnnotationNode annotation(
            AnnotationType type,
            int width,
            int height,
            int offsetX,
            int offsetY,
            String content) {
        return new AnnotationNode(
                type,
                "summary",
                AnnotationPlacement.TOP_LEFT,
                width,
                height,
                offsetX,
                offsetY,
                "审核人",
                content);
    }

    /** 创建使用测试专用字体的渲染器。 */
    private PdfDocumentRenderer renderer() {
        PdfFont font = new PdfFont("Droid Sans Fallback", this::openTestFont, true);
        return new PdfDocumentRenderer(List.of(font));
    }

    /** 每次调用重新打开测试字体。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }

    /** 生成难以压缩但可稳定复现的正文。 */
    private String largeRandomText(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder text = new StringBuilder(length);
        int state = 0x13579BDF;
        for (int index = 0; index < length; index++) {
            state = state * 1_103_515_245 + 12_345;
            int character = (state >>> 16) & 0x7FFF;
            text.append(index % 80 == 79 ? ' ' : alphabet.charAt(character % alphabet.length()));
        }
        return text.toString();
    }
}
