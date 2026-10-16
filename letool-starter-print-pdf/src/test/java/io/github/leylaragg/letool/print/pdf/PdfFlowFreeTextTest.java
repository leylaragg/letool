package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.PageSize;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ShapeNode;
import io.github.leylaragg.letool.print.document.node.InlineGroupNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.BoxSpacing;
import io.github.leylaragg.letool.print.document.style.CellBorder;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextAlignment;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.document.style.VerticalAlignment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.assertj.core.data.Offset;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 以最终 PDF 验证完整文本外观、正常流占位和每个物理副本的独立批注。 */
class PdfFlowFreeTextTest {

    @Test
    void shouldShowCompleteChineseTextWithConfiguredAppearance() throws Exception {
        String text = "这是完整中文批注".repeat(20) + "末尾标识";
        DocumentModel model = document(List.of(note("note", text), new ParagraphNode("after", "normal",
                List.of(new InternalLinkNode("note", List.of(new TextNode("后续正文")))))));
        try (PDDocument pdf = render(model)) {
            PDAnnotationFreeText annotation = (PDAnnotationFreeText) pdf.getPage(0).getAnnotations().get(0);
            assertThat(annotation.getContents()).isEqualTo(text);
            assertThat(annotation.isPrinted()).isTrue();
            assertThat(annotation.isReadOnly()).isFalse();
            assertThat(annotation.getDefaultAppearance()).contains("12");
            assertThat(annotation.getRectangle().getHeight()).isGreaterThan(100);
            assertThat(appearanceText(annotation).replaceAll("\\s+", "")).isEqualTo(text);
            assertThat(new PDFTextStripper().getText(pdf)).contains("后续正文").doesNotContain("末尾标识");
            assertThat(annotation.getCOSObject().containsKey(COSName.A)).isFalse();
            assertThat(annotation.getCOSObject().containsKey(COSName.AA)).isFalse();
            PDAnnotationLink link = (PDAnnotationLink) pdf.getPage(0).getAnnotations().get(1);
            assertThat(((PDPageDestination) link.getDestination()).retrievePageNumber()).isZero();
        }
    }

    @Test
    void shouldRepeatTableHeaderAndBothMarkedCellsOnEveryContinuation() throws Exception {
        List<TableRow> content = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            content.add(new TableRow(List.of(cell(paragraph("", "item-" + index), false))));
        }
        TableRow header = new TableRow(List.of(new TableCell(
                List.of(note("", "表头框")), 1, 3)));
        TableRow body = new TableRow(List.of(cell(paragraph("", "重复标签"), true),
                cell(new TableNode("", 0, content), false), cell(note("", "重复批注"), true)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 1, List.of(header, body)))))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).hasSize(2);
                assertThat(page.getAnnotations()).extracting(PDAnnotation::getContents)
                        .containsExactlyInAnyOrder("表头框", "重复批注");
                for (PDAnnotation annotation : page.getAnnotations()) {
                    assertThat(annotation).isInstanceOf(PDAnnotationFreeText.class);
                    assertThat(annotation.getRectangle().getLowerLeftY()).isGreaterThanOrEqualTo(0);
                }
            }
            assertThat(new PDFTextStripper().getText(pdf)).contains("item-39", "重复标签");
        }
    }

    /** 内容侧的多个独立块必须依次保留，不能只分段首块后清空其余内容。 */
    @Test
    void shouldKeepEveryContentBlockWhenSplittingThreeColumnRow() throws Exception {
        String first = "正文完整保留".repeat(80) + "首块末尾";
        String last = "第二独立块尾部标记";
        TableCell content = new TableCell(List.of(paragraph("", first), paragraph("", last)), 1, 1);
        TableRow row = new TableRow(List.of(cell(paragraph("", "重复标签"), true),
                content, cell(note("", "重复批注"), true)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 0, List.of(row)))))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", "").replace("重复标签", "");
            assertThat(text).contains(first + last).containsOnlyOnce(last);
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).extracting(PDAnnotation::getContents)
                        .containsExactly("重复批注");
            }
        }
    }

    /** 嵌套容器、内表和后续独立块混合时仍按完整原顺序重建每个片段。 */
    @Test
    void shouldKeepNestedMixedBlocksInOrderWhenSplittingContentCell() throws Exception {
        List<TableRow> rows = new ArrayList<>();
        StringBuilder expected = new StringBuilder("首段");
        for (int index = 0; index < 40; index++) {
            String token = "条目" + String.format("%03d", index);
            rows.add(new TableRow(List.of(cell(paragraph("", token), false))));
            expected.append(token);
        }
        expected.append("内层尾块外层尾块");
        SectionNode section = new SectionNode("", List.of(paragraph("", "首段"),
                new TableNode("", 0, rows), paragraph("", "内层尾块")));
        TableCell content = new TableCell(List.of(section, paragraph("", "外层尾块")), 1, 1);
        TableRow row = new TableRow(List.of(cell(paragraph("", "重复标签"), true),
                content, cell(note("", "重复批注"), true)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 0, List.of(row)))))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", "").replace("重复标签", "");
            assertThat(text).isEqualTo(expected.toString());
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).extracting(PDAnnotation::getContents)
                        .containsExactly("重复批注");
            }
        }
    }

    /** 只有表头的合法内表也是一个完整内容块，不能因没有 tbody 原子而被跳过。 */
    @Test
    void shouldKeepHeaderOnlyTableAfterLongContentBlock() throws Exception {
        String first = "正文完整保留".repeat(80);
        String last = "尾部完整表头";
        TableNode table = new TableNode("", 1, List.of(new TableRow(List.of(cell(paragraph("", last), false)))));
        TableCell content = new TableCell(List.of(paragraph("", first), table), 1, 1);
        TableRow row = new TableRow(List.of(cell(paragraph("", "重复标签"), true),
                content, cell(note("", "重复批注"), true)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 0, List.of(row)))))) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", "").replace("重复标签", "");
            assertThat(text).isEqualTo(first + last);
        }
    }

    /** 所有祖先表头都占据续页空间，三列字段片段不能再次跨物理页。 */
    @Test
    void shouldRepeatFieldUnderAllAncestorTableHeaders() throws Exception {
        assertNestedFieldPages(longParagraph(false, false), false);
    }

    /** colspan 使用真实单元格下标定位内容侧，且原生方框不能在文本行重建时丢失。 */
    @Test
    void shouldRepeatSpanningFieldAndKeepItsInlineSquareUnderAncestorHeaders() throws Exception {
        try (PDDocument pdf = render(nestedDocument(longParagraph(true, false), true))) {
            assertNestedFieldPages(pdf, true);
            assertThat(PdfSmallShapeTest.paintedPaths(pdf)).filteredOn(rectangle ->
                    Math.abs(rectangle.getWidth() - 8) < 0.06 && Math.abs(rectangle.getHeight() - 8) < 0.06)
                    .hasSize(1);
        }
    }

    /** 行内 FreeText 与后续长内容共同分片时，原生替换对象必须完整进入某个真实片段。 */
    @Test
    void shouldKeepInlineFreeTextWhenLongParagraphIsFragmented() throws Exception {
        try (PDDocument pdf = render(nestedDocument(longParagraph(false, true), false))) {
            int count = 0;
            for (PDPage page : pdf.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if ("行内文本框".equals(annotation.getContents())) {
                        count++;
                        assertThat(appearanceText(annotation).replaceAll("\\s+", "")).isEqualTo("行内文本框");
                    }
                }
            }
            assertThat(count).isEqualTo(1);
            assertNestedFieldPages(pdf, false);
        }
    }

    private void assertNestedFieldPages(BlockNode content, boolean spanning) throws Exception {
        try (PDDocument pdf = render(nestedDocument(content, spanning))) {
            assertNestedFieldPages(pdf, spanning);
        }
    }

    private static void assertNestedFieldPages(PDDocument pdf, boolean spanning) throws Exception {
        assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
        PDFTextStripper stripper = new PDFTextStripper();
        String all = stripper.getText(pdf).replaceAll("\\s+", "");
        for (int index = 0; index < 80; index++) {
            assertThat(all).containsOnlyOnce("内容行" + String.format("%03d", index));
        }
        for (int page = 0; page < pdf.getNumberOfPages(); page++) {
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            String text = stripper.getText(pdf);
            if (text.contains("内容行")) {
                assertThat(text).contains("记录表头");
                assertThat(pdf.getPage(page).getAnnotations()).extracting(PDAnnotation::getContents)
                        .contains("外层表头", "字段批注");
                if (!spanning) {
                    assertThat(text).contains("重复标签");
                }
            }
        }
    }

    private static ParagraphNode longParagraph(boolean square, boolean inlineNote) {
        List<InlineNode> lines = new ArrayList<>();
        if (square) {
            lines.add(new ShapeNode(ShapeNode.Kind.SQUARE, 8, 8, 0.5, false, 1, 0));
        }
        if (inlineNote) {
            lines.add(fit("行内文本框"));
            lines.add(LineBreakNode.INSTANCE);
        }
        for (int index = 0; index < 80; index++) {
            lines.add(new TextNode("内容行" + String.format("%03d", index)));
            lines.add(LineBreakNode.INSTANCE);
        }
        return new ParagraphNode("", "normal", lines);
    }

    private static DocumentModel nestedDocument(BlockNode content, boolean spanning) {
        TableCell values = new TableCell(List.of(content), 1, spanning ? 2 : 1);
        TableCell annotation = cell(note("", "字段批注"), true);
        TableRow field = new TableRow(spanning ? List.of(values, annotation)
                : List.of(cell(paragraph("", "重复标签"), true), values, annotation));
        TableRow recordHeader = new TableRow(List.of(new TableCell(
                List.of(paragraph("", "记录表头")), 1, 3)), 35, true);
        TableNode record = new TableNode("", "three", 1, List.of(recordHeader, field));
        TableRow title = new TableRow(List.of(cell(fit("外层表头"), false)), 70, true);
        TableRow body = new TableRow(List.of(new TableCell("outer", List.of(record), 1, 1)));
        return document(List.of(new TableNode("", "frame", 1, List.of(title, body))));
    }

    @Test
    void shouldRepeatRunningHeaderAndKeepFitContentBoxesIndependent() throws Exception {
        List<BlockNode> body = new ArrayList<>();
        body.add(new ParagraphNode("", "right", List.of(
                new FreeTextNode("", "note", "box", "", "甲框", FreeTextNode.WidthMode.FIT_CONTENT, null),
                new TextNode(" "),
                new FreeTextNode("", "note", "box", "", "乙框", FreeTextNode.WidthMode.FIT_CONTENT,
                        DocumentColor.rgb(128, 192, 224)))));
        for (int index = 0; index < 45; index++) {
            body.add(paragraph("", "正文 " + index));
        }
        PageLayout layout = smallPage();
        PageSequence sequence = new PageSequence(layout, new PageRegion(List.of(note("", "页眉框"))),
                PageRegion.empty(), PageNumbering.counted(), body);
        DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles(), List.of(sequence));
        try (PDDocument pdf = render(model)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).extracting(PDAnnotation::getContents).contains("页眉框");
            }
            assertThat(pdf.getPage(0).getAnnotations()).hasSize(3);
            PDAnnotation first = pdf.getPage(0).getAnnotations().stream()
                    .filter(annotation -> "甲框".equals(annotation.getContents())).findFirst().orElseThrow();
            PDAnnotation second = pdf.getPage(0).getAnnotations().stream()
                    .filter(annotation -> "乙框".equals(annotation.getContents())).findFirst().orElseThrow();
            assertThat(first.getRectangle().getWidth()).isLessThan(100);
            assertThat(second.getRectangle().getLowerLeftX()).isGreaterThan(first.getRectangle().getUpperRightX());
        }
    }

    /** 收窄文本框作为原生行内对象换行，组内间隔与右对齐均由既有段落布局处理。 */
    @Test
    void shouldWrapFitContentGroupWithFourPointGapsInsideNarrowCell() throws Exception {
        ShapeNode gap = new ShapeNode(ShapeNode.Kind.SPACE, 4, 1, 0.5, false, 1, 0);
        ParagraphNode group = new ParagraphNode("", "right", List.of(fit("甲"),
                new InlineGroupNode(List.of(gap, fit("乙")), false).withKeepTogether(),
                new InlineGroupNode(List.of(gap, fit("丙丁戊己")), false).withKeepTogether()));
        TableRow row = new TableRow(List.of(cell(paragraph("", "标题"), false),
                cell(paragraph("", ""), false), cell(group, false)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 0, List.of(row)))))) {
            List<PDAnnotation> annotations = pdf.getPage(0).getAnnotations();
            assertThat(annotations).hasSize(3);
            PDAnnotation first = annotations.get(0);
            PDAnnotation second = annotations.get(1);
            PDAnnotation last = annotations.get(2);
            assertThat(second.getRectangle().getLowerLeftX() - first.getRectangle().getUpperRightX())
                    .isCloseTo(4, Offset.offset(0.1F));
            assertThat(last.getRectangle().getUpperRightY()).isLessThanOrEqualTo(
                    first.getRectangle().getLowerLeftY() + 0.1F);
            assertThat(last.getRectangle().getUpperRightX()).isCloseTo(second.getRectangle().getUpperRightX(),
                    Offset.offset(0.1F));
        }
    }

    /** 长名称的框在组内扣除间隔后完整换行，不把组挤出父列。 */
    @Test
    void shouldWrapLongFitContentInsideTheRemainingGroupWidth() throws Exception {
        String content = "长名称完整展示".repeat(10);
        ShapeNode gap = new ShapeNode(ShapeNode.Kind.SPACE, 4, 1, 0.5, false, 1, 0);
        ParagraphNode group = new ParagraphNode("", "right", List.of(
                new InlineGroupNode(List.of(gap, fit(content)), false).withKeepTogether()));
        TableRow row = new TableRow(List.of(cell(paragraph("", "标题"), false),
                cell(paragraph("", ""), false), cell(group, false)));
        try (PDDocument pdf = render(document(List.of(new TableNode("", "three", 0, List.of(row)))))) {
            PDAnnotation annotation = pdf.getPage(0).getAnnotations().get(0);
            assertThat(annotation.getRectangle().getWidth()).isLessThan(96);
            assertThat(annotation.getRectangle().getHeight()).isGreaterThan(100);
            assertThat(appearanceText(annotation).replaceAll("\\s+", "")).isEqualTo(content);
        }
    }

    @Test
    void shouldRejectGroupWhoseFixedSpacingLeavesNoTextWidth() {
        ShapeNode gap = new ShapeNode(ShapeNode.Kind.SPACE, 500, 1, 0.5, false, 1, 0);
        ParagraphNode group = new ParagraphNode("", "right", List.of(
                new InlineGroupNode(List.of(gap, fit("完整内容")), false).withKeepTogether()));
        assertThatThrownBy(() -> render(document(List.of(group)))).hasMessageContaining("完整");
    }

    private static FreeTextNode fit(String content) {
        return new FreeTextNode("", "note", "box", "", content, FreeTextNode.WidthMode.FIT_CONTENT, null);
    }

    @Test
    void shouldRejectCompleteBoxThatCannotFitOnOnePage() {
        assertThatThrownBy(() -> render(document(List.of(note("", "无法裁切".repeat(300))))))
                .hasMessageContaining("完整");
    }

    @Test
    void shouldFullyEmbedAppearanceFontWhenRequested() throws Exception {
        DocumentModel source = document(List.of(note("", "完整嵌入")));
        DocumentModel model = new DocumentModel(source.metadata(), source.styleSheet(), source.pageSequences(),
                DocumentOptions.builder().fullFontEmbedding(true).build());
        try (PDDocument pdf = render(model)) {
            PDAppearanceStream appearance = pdf.getPage(0).getAnnotations().get(0).getNormalAppearanceStream();
            for (COSName name : appearance.getResources().getFontNames()) {
                PDFont font = appearance.getResources().getFont(name);
                assertThat(font.isEmbedded()).isTrue();
                assertThat(font.getName()).doesNotMatch("^[A-Z]{6}\\+.*");
            }
        }
    }

    @Test
    void shouldCountPhysicalHeaderCopiesAcrossAllMergedSequences() {
        List<BlockNode> header = new ArrayList<>();
        for (int index = 0; index < 26; index++) {
            header.add(new FreeTextNode("", "tiny", "", "", "批注"));
        }
        List<BlockNode> body = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            body.add(new ParagraphNode("", List.of(new TextNode("正文"))));
            if (index < 19) {
                body.add(PageBreakNode.INSTANCE);
            }
        }
        StyleSheet styles = StyleSheet.builder().text("tiny", TextStyle.builder()
                .fontFamily("Droid Sans Fallback").fontSize(DocumentLength.points(1)).lineHeight(1).build()).build();
        PageSequence sequence = new PageSequence(smallPage(), new PageRegion(header), PageRegion.empty(),
                PageNumbering.counted(), body);
        DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles, List.of(sequence, sequence));
        assertThatThrownBy(() -> render(model)).hasMessageContaining("1000");
    }

    private static TableCell cell(BlockNode node, boolean repeat) {
        return new TableCell("", List.of(node), 1, 1, repeat);
    }

    private static FreeTextNode note(String id, String text) {
        return new FreeTextNode(id, "note", "box", "审核人", text);
    }

    private static ParagraphNode paragraph(String id, String text) {
        return new ParagraphNode(id, "normal", List.of(new TextNode(text)));
    }

    private static DocumentModel document(List<BlockNode> body) {
        return new DocumentModel(DocumentMetadata.empty(), styles(), List.of(PageSequence.body(smallPage(), body)));
    }

    private static PageLayout smallPage() {
        return new PageLayout(new PageSize(120_000, 150_000), PageOrientation.PORTRAIT,
                new Margins(15_000, 10_000, 10_000, 10_000));
    }

    private static StyleSheet styles() {
        return StyleSheet.builder()
                .text("note", TextStyle.builder().fontFamily("Droid Sans Fallback")
                        .fontSize(DocumentLength.points(12)).lineHeight(1.5)
                        .color(DocumentColor.rgb(16, 32, 48)).build())
                .paragraph("normal", ParagraphStyle.builder().textStyleName("note").build())
                .paragraph("right", ParagraphStyle.builder().textStyleName("note")
                        .alignment(TextAlignment.RIGHT).build())
                .cell("outer", CellStyle.builder().padding(BoxSpacing.all(DocumentLength.millimeters(2.822222)))
                        .verticalAlignment(VerticalAlignment.MIDDLE).build())
                .cell("box", CellStyle.builder().background(DocumentColor.rgb(224, 240, 255))
                        .padding(BoxSpacing.all(DocumentLength.millimeters(1)))
                        .borders(CellBorder.of(BorderLineStyle.SOLID, DocumentLength.points(0.5),
                                DocumentColor.rgb(50, 70, 100))).build())
                .table("frame", TableStyle.builder().layoutMode(TableLayoutMode.FIXED)
                        .columnWidths(List.of(DocumentLength.percent(100))).build())
                .table("three", TableStyle.builder().layoutMode(TableLayoutMode.FIXED)
                        .columnWidths(List.of(DocumentLength.percent(29.25), DocumentLength.percent(35.75),
                                DocumentLength.percent(35))).build())
                .build();
    }

    private PDDocument render(DocumentModel document) throws Exception {
        PdfFont font = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL, this::font, true);
        byte[] bytes = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font))),
                document, RenderOptions.defaults()).content();
        return Loader.loadPDF(bytes);
    }

    private InputStream font() {
        return Objects.requireNonNull(getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"));
    }

    /** 单独读取普通外观流，防止只在 Contents 保存了不可见的尾部内容。 */
    private static String appearanceText(PDAnnotation annotation) throws Exception {
        PDAppearanceStream appearance = annotation.getNormalAppearanceStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(appearance.getBBox());
            page.setResources(appearance.getResources());
            page.setContents(new PDStream(appearance.getCOSObject()));
            document.addPage(page);
            return new PDFTextStripper().getText(document);
        }
    }
}
