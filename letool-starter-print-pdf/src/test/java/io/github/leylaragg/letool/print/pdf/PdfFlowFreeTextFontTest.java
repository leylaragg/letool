package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.style.BoxSpacing;
import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.CellBorder;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用宿主授权的字体目录建立独立类路径，避免把商业字体复制进 SDK。 */
@EnabledIfSystemProperty(named = "letool.test.fonts", matches = ".+")
class PdfFlowFreeTextFontTest {

    @Test
    void shouldUseClasspathSimSunTtcAndTimesWithIdenticalMeasuredAppearanceMetrics() throws Exception {
        Path directory = Path.of(System.getProperty("letool.test.fonts"));
        try (URLClassLoader resources = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            PdfFont simsun = new PdfFont("SimSun", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("simsun.ttc")), true);
            PdfFont times = new PdfFont("Times New Roman", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("times.ttf")), false);
            StyleSheet styles = StyleSheet.builder()
                    .text("cn", TextStyle.builder().fontFamily("SimSun")
                            .fontSize(DocumentLength.points(10)).lineHeight(1.5).build())
                    .text("en", TextStyle.builder().fontFamily("Times New Roman")
                            .fontSize(DocumentLength.points(12)).lineHeight(1.5).build())
                    .paragraph("flow", ParagraphStyle.builder().textStyleName("en").build())
                    .cell("box", CellStyle.builder().padding(BoxSpacing.all(DocumentLength.millimeters(1))).build())
                    .build();
            String chinese = "宋体中文完整批注与末尾内容".repeat(12);
            String english = "AE.AETERM";
            String mixed = "CE.SYMPTOM\n1 = 第一项; 2 = 其他";
            DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles,
                    List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                            new FreeTextNode("cn", "cn", "box", "", chinese),
                            new ParagraphNode("", "flow", List.of(new FreeTextNode("en", "en", "box", "", english,
                                    FreeTextNode.WidthMode.FIT_CONTENT, null))),
                            new FreeTextNode("mixed", "en", "box", "", mixed)))),
                    DocumentOptions.builder().defaultFontFamily("Times New Roman").glyphFallbackFamily("SimSun")
                            .fullFontEmbedding(true).build());
            byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(times, simsun))),
                    model, RenderOptions.defaults()).content();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                assertThat(pdf.getPage(0).getAnnotations()).hasSize(3);
                PDAnnotation cn = annotation(pdf, chinese);
                PDAnnotation en = annotation(pdf, english);
                assertThat(appearanceText(cn).replaceAll("\\s+", "")).isEqualTo(chinese);
                assertThat(appearanceText(en).replaceAll("\\s+", "")).isEqualTo(english);
                assertThat(en.getRectangle().getHeight()).isLessThan(30);
                COSName fontName = en.getNormalAppearanceStream().getResources().getFontNames().iterator().next();
                PDFont font = en.getNormalAppearanceStream().getResources().getFont(fontName);
                float expectedWidth = font.getStringWidth(english) / 1000 * 12 + 2 * 72 / 25.4F;
                assertThat(en.getRectangle().getWidth()).isCloseTo(expectedWidth,
                        Offset.offset(0.1F));
                assertThat(font.getName()).isEqualTo("TimesNewRomanPSMT");
                PDAnnotation combined = annotation(pdf, mixed);
                assertThat(appearanceText(combined).replaceAll("\\s+", ""))
                        .isEqualTo(mixed.replaceAll("\\s+", ""));
                List<String> names = new ArrayList<>();
                for (COSName name : combined.getNormalAppearanceStream().getResources().getFontNames()) {
                    names.add(combined.getNormalAppearanceStream().getResources().getFont(name).getName());
                }
                assertThat(names).containsExactlyInAnyOrder("TimesNewRomanPSMT", "SimSun");
                assertMixedFonts(combined);
            }
        }
    }

    @Test
    void shouldKeepFitContentOnOneLineAcrossFractionalGlyphWidths() throws Exception {
        Path directory = Path.of(System.getProperty("letool.test.fonts"));
        try (URLClassLoader resources = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            PdfFont times = new PdfFont("Times New Roman", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("times.ttf")), true);
            StyleSheet styles = StyleSheet.builder().text("en", TextStyle.builder()
                    .fontFamily("Times New Roman").fontSize(DocumentLength.points(11)).lineHeight(1.5).build())
                    .cell("box", CellStyle.builder().padding(BoxSpacing.all(DocumentLength.millimeters(1))).build())
                    .build();
            List<BlockNode> boxes = new ArrayList<>();
            for (String word : List.of("A", "W", "I", "Mm", "VISIT", "AE.AETERM", "SUBJID", "WWMW")) {
                boxes.add(new FreeTextNode("", "en", "box", "", word, FreeTextNode.WidthMode.FIT_CONTENT, null));
            }
            DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles,
                    List.of(PageSequence.body(PageLayout.a4Portrait(), boxes)));
            byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(times))),
                    model, RenderOptions.defaults()).content();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                assertThat(pdf.getPage(0).getAnnotations()).hasSize(boxes.size());
                for (PDAnnotation annotation : pdf.getPage(0).getAnnotations()) {
                    assertThat(annotation.getRectangle().getHeight()).isLessThan(25);
                    assertThat(appearanceText(annotation).replaceAll("\\s+", "")).isEqualTo(annotation.getContents());
                }
            }
        }
    }

    /** 编辑器按标准属性重建外观时，边框仍与原测量矩形重合，不能把文字内边距再次扣到边框上。 */
    @Test
    void shouldKeepMeasuredBorderBoundsWhenViewerRebuildsFreeTextAppearance() throws Exception {
        Path directory = Path.of(System.getProperty("letool.test.fonts"));
        try (URLClassLoader resources = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            PdfFont times = new PdfFont("Times New Roman", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("times.ttf")), true);
            StyleSheet styles = StyleSheet.builder().text("en", TextStyle.builder()
                    .fontFamily("Times New Roman").fontSize(DocumentLength.points(10.5)).lineHeight(4.0 / 3).build())
                    .cell("box", CellStyle.builder().padding(BoxSpacing.all(DocumentLength.millimeters(25.4 / 72)))
                            .borders(CellBorder.of(BorderLineStyle.SOLID,
                                    DocumentLength.points(0.5), DocumentColor.BLACK)).build())
                    .build();
            DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles,
                    List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                            new FreeTextNode("", "en", "box", "", "MH", FreeTextNode.WidthMode.FIT_CONTENT, null)))));
            byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(times))),
                    model, RenderOptions.defaults()).content();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                PDAnnotationFreeText annotation = (PDAnnotationFreeText) annotation(pdf, "MH");
                float measuredWidth = annotation.getRectangle().getWidth();
                float measuredHeight = annotation.getRectangle().getHeight();
                assertThat(appearanceText(annotation).trim()).isEqualTo("MH");

                // 模拟阅读器进入编辑状态时依据批注字典重建，不能只检查我们自己写出的 AP。
                annotation.constructAppearances(pdf);
                assertThat(annotation.getNormalAppearanceStream().getBBox().getWidth())
                        .isCloseTo(measuredWidth, Offset.offset(0.01F));
                assertThat(annotation.getNormalAppearanceStream().getBBox().getHeight())
                        .isCloseTo(measuredHeight, Offset.offset(0.01F));
                assertThat(annotation.getRectangle().getWidth()).isEqualTo(measuredWidth);
            }
        }
    }

    /** 空文本样式应采用节点默认值，不能从父段落继承另一套测量字体。 */
    @Test
    void shouldUseExplicitDefaultTextMetricsInsideDifferentlyStyledParagraph() throws Exception {
        Path directory = Path.of(System.getProperty("letool.test.fonts"));
        try (URLClassLoader resources = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            PdfFont simsun = new PdfFont("SimSun", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("simsun.ttc")), true);
            PdfFont times = new PdfFont("Times New Roman", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("times.ttf")), false);
            StyleSheet styles = StyleSheet.builder().text("parent", TextStyle.builder()
                    .fontFamily("Times New Roman").fontSize(DocumentLength.points(18))
                    .lineHeight(2).color(DocumentColor.rgb(255, 0, 0)).build())
                    .paragraph("parent", ParagraphStyle.builder().textStyleName("parent").build()).build();
            FreeTextNode box = new FreeTextNode("", "", "", "", "ABC", FreeTextNode.WidthMode.FIT_CONTENT, null);
            DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles,
                    List.of(PageSequence.body(PageLayout.a4Portrait(),
                            List.of(new ParagraphNode("", "parent", List.of(box))))),
                    DocumentOptions.builder().defaultFontFamily("SimSun").fullFontEmbedding(true).build());
            byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(times, simsun))),
                    model, RenderOptions.defaults()).content();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                PDAnnotationFreeText annotation = (PDAnnotationFreeText) pdf.getPage(0).getAnnotations().get(0);
                List<String> names = new ArrayList<>();
                for (COSName name : annotation.getNormalAppearanceStream().getResources().getFontNames()) {
                    names.add(annotation.getNormalAppearanceStream().getResources().getFont(name).getName());
                }
                assertThat(names).containsExactly("SimSun");
                assertThat(annotation.getDefaultAppearance()).contains("10.0 Tf", "0.0 0.0 0.0 rg");
                assertThat(annotation.getDefaultStyleString()).contains("SimSun", "font-size:10.0pt");
                assertThat(appearanceText(annotation).strip()).isEqualTo("ABC");
            }
        }
    }

    /** 正文全由回退字体绘制时，默认编辑字体不能作为空子集保存。 */
    @Test
    void shouldKeepPrimaryEditingFontWhenOnlyFallbackGlyphsAreDrawn() throws Exception {
        Path directory = Path.of(System.getProperty("letool.test.fonts"));
        try (URLClassLoader resources = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            PdfFont simsun = new PdfFont("SimSun", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("simsun.ttc")), true);
            PdfFont times = new PdfFont("Times New Roman", FontWeight.NORMAL,
                    () -> Objects.requireNonNull(resources.getResourceAsStream("times.ttf")), false);
            FreeTextNode box = new FreeTextNode("", "", "", "", "纯中文正文");
            DocumentModel model = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(),
                    List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(box))),
                    DocumentOptions.builder().defaultFontFamily("Times New Roman").glyphFallbackFamily("SimSun")
                            .fullFontEmbedding(true).build());
            byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(times, simsun))),
                    model, RenderOptions.defaults()).content();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                PDAnnotationFreeText annotation = (PDAnnotationFreeText) pdf.getPage(0).getAnnotations().get(0);
                assertThat(appearanceText(annotation).strip()).isEqualTo("纯中文正文");
                assertThat(annotation.getDefaultStyleString()).contains("Times New Roman");
            }
        }
    }

    /** 直接检查外观中的字形字体，防止仅嵌入两个字体却把整框换成宋体。 */
    private static void assertMixedFonts(PDAnnotation annotation) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(annotation.getNormalAppearanceStream().getBBox());
            page.setResources(annotation.getNormalAppearanceStream().getResources());
            page.setContents(new PDStream(annotation.getNormalAppearanceStream().getCOSObject()));
            pdf.addPage(page);
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    boolean ascii = text.getUnicode().chars().allMatch(character -> character < 128);
                    assertThat(text.getFont().getName()).isEqualTo(ascii ? "TimesNewRomanPSMT" : "SimSun");
                    super.processTextPosition(text);
                }
            };
            stripper.getText(pdf);
        }
    }

    /** 批注数组顺序由绘制阶段决定，按内容选择本用例的独立框。 */
    private static PDAnnotation annotation(PDDocument pdf, String content) throws Exception {
        for (PDAnnotation annotation : pdf.getPage(0).getAnnotations()) {
            if (content.equals(annotation.getContents())) {
                return annotation;
            }
        }
        throw new AssertionError("测试文本框不存在");
    }

    private static String appearanceText(PDAnnotation annotation) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(annotation.getNormalAppearanceStream().getBBox());
            page.setResources(annotation.getNormalAppearanceStream().getResources());
            page.setContents(new PDStream(annotation.getNormalAppearanceStream().getCOSObject()));
            pdf.addPage(page);
            return new PDFTextStripper().getText(pdf);
        }
    }
}
