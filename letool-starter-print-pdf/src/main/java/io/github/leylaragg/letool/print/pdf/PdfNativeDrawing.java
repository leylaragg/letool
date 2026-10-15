package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BodyCaptionNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在原生排版之后追加精确线条与页内图例，随后仍走统一合并和最终字体处理。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfNativeDrawing {

    /** PDF 坐标使用点，页面模型以微米保存物理长度。 */
    private static final float POINTS_PER_INCH = 72f;
    private static final int MICROMETERS_PER_INCH = 25_400;

    /** PDF 字体宽度以千分之一字体单位提供。 */
    private static final int FONT_WIDTH_UNITS = 1000;
    private static final float UNDERLINE_WIDTH_POINTS = 0.5f;
    private static final double POSITION_TOLERANCE_POINTS = 0.025;
    private static final float MAX_COLOR_CHANNEL = 255f;

    private PdfNativeDrawing() {
    }

    /**
     * 在既有页面上追加下划线和正文图例，不改变原生排版结果。
     *
     * @param pdf 本次排版的 PDF；调用方负责保存和关闭
     * @param marks 以 PDF 点坐标表示的逐页绘制位置
     * @param captions 使用正文框底部定位的图例
     * @param styles 文档命名样式
     * @param options 文档字体默认值
     * @param layout 页面物理尺寸与边距
     * @param catalog 宿主字体目录
     * @throws IOException 图例不是单行文本、缺少字体、位置越界或 PDF 写入失败
     */
    static void write(PDDocument pdf, PdfNativeMarks.Marks marks, List<BodyCaptionNode> captions,
        StyleSheet styles, DocumentOptions options, PageLayout layout, PdfFontCatalog catalog) throws IOException {
        // 字体缓存只在本次 PDF 内共享，避免把文档所属字体对象带入其他渲染。
        Map<String, PDType0Font> fonts = new LinkedHashMap<>();
        for (int page = 0; page < pdf.getNumberOfPages(); page++) {
            try (PDPageContentStream content = new PDPageContentStream(pdf, pdf.getPage(page),
                PDPageContentStream.AppendMode.APPEND, true, true)) {
                content.setStrokingColor(0f);
                content.setLineWidth(UNDERLINE_WIDTH_POINTS);
                for (PdfNativeMarks.Line line : marks.underlines()) {
                    if (line.page() == page) {
                        content.moveTo(line.left(), line.y());
                        content.lineTo(line.right(), line.y());
                        content.stroke();
                    }
                }

                // 图例以本页实际正文框底定位，无正文框位置的页面不追加图例。
                Float frameBottom = marks.frameBottoms().get(page);
                if (frameBottom != null) {
                    for (BodyCaptionNode caption : captions) {
                        float x = layout.margins().leftMicrometers() * POINTS_PER_INCH / MICROMETERS_PER_INCH;
                        float y = frameBottom - (float) caption.baselineOffsetPoints();
                        for (InlineNode inline : caption.children()) {
                            if (!(inline instanceof TextNode text)) {
                                throw new IOException("正文图例仅支持完整单行文字");
                            }
                            TextStyle style = styles.resolveText(
                                text.styleName().isEmpty() ? caption.textStyleName() : text.styleName());
                            String family = style.fontFamily().isEmpty()
                                ? options.defaultFontFamily() : style.fontFamily();
                            PdfFont source = family.isEmpty()
                                ? catalog.defaultFace(style.fontWeight()).orElseThrow(() -> new IOException("图例字体缺失"))
                                : catalog.requireFace(family, style.fontWeight(), style.fontPosture());
                            String key = source.familyName() + ":" + source.weight() + ":" + source.posture();
                            PDType0Font font = fonts.get(key);
                            if (font == null) {
                                font = PDType0Font.load(
                                    pdf, new ByteArrayInputStream(PdfFontSource.read(source)), true);
                                fonts.put(key, font);
                            }

                            // 在写入前验证完整文字宽度和基线位置，越界时不截断图例。
                            float size = (float) style.fontSize().value();
                            float width = font.getStringWidth(text.text()) * size / FONT_WIDTH_UNITS;
                            float right = pdf.getPage(page).getMediaBox().getWidth()
                                - layout.margins().rightMicrometers() * POINTS_PER_INCH / MICROMETERS_PER_INCH;
                            if (x + width > right + POSITION_TOLERANCE_POINTS || y < 0) {
                                throw new IOException("正文图例没有合法排版位置");
                            }

                            content.setNonStrokingColor(style.color().red() / MAX_COLOR_CHANNEL,
                                style.color().green() / MAX_COLOR_CHANNEL, style.color().blue() / MAX_COLOR_CHANNEL);
                            content.beginText();
                            content.setFont(font, size);
                            content.newLineAtOffset(x, y);
                            content.showText(text.text());
                            content.endText();
                            x += width;
                        }
                    }
                }
            }
        }
    }
}
