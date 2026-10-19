package io.github.leylaragg.letool.print.pdf.internal.annotation;

import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pdf.internal.font.PdfFontSource;

import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.CellBorder;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将每次原生绘制产生的位置转换为独立 FreeText 对象，完整外观只写入批注。
 *
 * @author leyland
 */
final class PdfFreeTextWriter {

    /** 此标记只存在于内部排版文件，交付前必须被全部替换。 */

    private static final float GEOMETRY_TOLERANCE = 0.1F;
    private static final float COLOR_CHANNEL_MAX = 255F;

    private PdfFreeTextWriter() {
    }

    /** 每个物理标记都创建新对象，不按 ID 或正文去重。 */
    static void write(PDDocument pdf, Map<String, PdfFreeTextLayout.MeasuredBox> boxes) throws IOException {
        Map<PdfFont, PDFont> fonts = new IdentityHashMap<>();
        Set<String> rendered = new HashSet<>();
        int count = 0;
        for (PDPage page : pdf.getPages()) {
            List<PDAnnotation> annotations = new ArrayList<>();
            for (PDAnnotation annotation : page.getAnnotations()) {
                String key = markerKey(annotation);
                if (key == null) {
                    annotations.add(annotation);
                    continue;
                }
                PdfFreeTextLayout.MeasuredBox box = boxes.get(key);
                if (box == null) {
                    throw PrintValidationException.invalidDocument("流式文本框绘制位置缺少测量快照");
                }
                if (++count > PdfAnnotations.MAX_ANNOTATIONS) {
                    throw PrintValidationException.invalidDocument("PDF 批注数量超过 "
                            + PdfAnnotations.MAX_ANNOTATIONS);
                }
                rendered.add(key);
                requireCompleteRectangle(page, annotation.getRectangle(), box);
                annotations.add(create(pdf, annotation.getRectangle(), box, fonts));
            }
            page.setAnnotations(annotations);
        }
        if (!rendered.containsAll(boxes.keySet())) {
            throw PrintValidationException.invalidDocument("流式文本框未产生最终绘制位置");
        }
    }

    /** 只识别渲染器生成的内部标记，保留文档已有导航。 */
    private static String markerKey(PDAnnotation annotation) {
        if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action
                && action.getURI() != null && action.getURI().startsWith(PdfAnnotations.MARKER_PREFIX)) {
            return action.getURI().substring(PdfAnnotations.MARKER_PREFIX.length());
        }
        return null;
    }

    /** 原生引擎若裁切不可分框，拒绝输出，而不是让 Contents 掩盖缺失的可见文字。 */
    private static void requireCompleteRectangle(PDPage page, PDRectangle rectangle,
            PdfFreeTextLayout.MeasuredBox box) {
        PDRectangle crop = page.getCropBox();
        if (Math.abs(rectangle.getWidth() - box.width()) > GEOMETRY_TOLERANCE
                || Math.abs(rectangle.getHeight() - box.height()) > GEOMETRY_TOLERANCE
                || rectangle.getLowerLeftX() < crop.getLowerLeftX() - GEOMETRY_TOLERANCE
                || rectangle.getLowerLeftY() < crop.getLowerLeftY() - GEOMETRY_TOLERANCE
                || rectangle.getUpperRightX() > crop.getUpperRightX() + GEOMETRY_TOLERANCE
                || rectangle.getUpperRightY() > crop.getUpperRightY() + GEOMETRY_TOLERANCE) {
            throw PrintValidationException.invalidDocument("完整文本框没有合法页面位置：测量 "
                    + box.width() + " x " + box.height() + "，实际 " + rectangle);
        }
    }

    /** 使用测量时的同一行、字体和基线创建可打印且可编辑的外观。 */
    private static PDAnnotationFreeText create(PDDocument pdf, PDRectangle rectangle,
            PdfFreeTextLayout.MeasuredBox box, Map<PdfFont, PDFont> fonts) throws IOException {
        PDFont font = font(pdf, box.font(), fonts);
        PDAnnotationFreeText annotation = new PDAnnotationFreeText();
        annotation.setRectangle(rectangle);
        annotation.setContents(box.content());
        annotation.setTitlePopup(box.author());
        annotation.setPrinted(true);
        annotation.setReadOnly(false);
        annotation.setLocked(false);
        if (box.background() != null) {
            annotation.setColor(color(box.background()));
        }
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(PdfFreeTextLayout.points(box.boxStyle().topBorder().width()));
        annotation.setBorderStyle(border);
        PDAppearanceStream appearance = new PDAppearanceStream(pdf);
        appearance.setBBox(new PDRectangle(box.width(), box.height()));
        PDResources resources = new PDResources();
        COSName fontName = resources.add(font);
        appearance.setResources(resources);
        DocumentColor textColor = box.textStyle().color();
        float size = (float) box.textStyle().fontSize().value();
        annotation.setDefaultAppearance("/" + fontName.getName() + " " + size + " Tf "
                + textColor.red() / COLOR_CHANNEL_MAX + " " + textColor.green() / COLOR_CHANNEL_MAX + " "
                + textColor.blue() / COLOR_CHANNEL_MAX + " rg");
        annotation.setDefaultStyleString("font-family:'" + box.font().familyName().replace("'", "\\'")
                + "';font-size:" + size + "pt;line-height:" + box.textStyle().lineHeight()
                + ";color:" + hex(textColor));
        // RD 表示 Rect 与边框的差值，不是文字内边距；当前边框贴合 Rect，保持默认零偏移。
        // 文字留白已由测量和 AP 外观处理，重复写入 RD 会让阅读器重建时再次缩窄正文区域。
        try (PDPageContentStream stream = new PDPageContentStream(pdf, appearance)) {
            paintBox(stream, box);
            stream.beginText();
            stream.setNonStrokingColor(color(textColor));
            stream.setFont(font, size);
            stream.setLeading(box.lineAdvance());
            stream.newLineAtOffset(box.textLeft(), box.height() - box.firstBaselineFromTop());
            for (int index = 0; index < box.lines().size(); index++) {
                if (index > 0) {
                    stream.newLine();
                }
                for (PdfFreeTextLayout.TextRun run : box.lines().get(index).runs()) {
                    stream.setFont(font(pdf, run.font(), fonts), size);
                    stream.showText(run.text());
                }
            }
            stream.endText();
        }
        PDAppearanceDictionary dictionary = new PDAppearanceDictionary();
        dictionary.setNormalAppearance(appearance);
        annotation.setAppearance(dictionary);
        return annotation;
    }

    /** 外观字体仅在当前 PDF 内共享，所有片段都来自原生测量所选的真实字体源。 */
    private static PDFont font(PDDocument pdf, PdfFont source, Map<PdfFont, PDFont> fonts) throws IOException {
        PDFont font = fonts.get(source);
        if (font == null) {
            font = PDType0Font.load(pdf, new ByteArrayInputStream(PdfFontSource.read(source)), true);
            fonts.put(source, font);
        }
        return font;
    }

    /** 按各边原有样式绘制填充和边框，不向正文重复绘制。 */
    private static void paintBox(PDPageContentStream stream, PdfFreeTextLayout.MeasuredBox box) throws IOException {
        if (box.background() != null) {
            stream.setNonStrokingColor(color(box.background()));
            stream.addRect(0, 0, box.width(), box.height());
            stream.fill();
        }
        CellStyle style = box.boxStyle();
        paintBorder(stream, style.topBorder(), 0, box.height(), box.width(), box.height(), 0, -1);
        paintBorder(stream, style.rightBorder(), box.width(), 0, box.width(), box.height(), -1, 0);
        paintBorder(stream, style.bottomBorder(), 0, 0, box.width(), 0, 0, 1);
        paintBorder(stream, style.leftBorder(), 0, 0, 0, box.height(), 1, 0);
    }

    /** 将边线全部收在外框以内，双线使用声明宽度的三等分。 */
    private static void paintBorder(PDPageContentStream stream, CellBorder border, float x1, float y1,
            float x2, float y2, float inwardX, float inwardY) throws IOException {
        if (border.lineStyle() == BorderLineStyle.NONE) {
            return;
        }
        float width = PdfFreeTextLayout.points(border.width());
        stream.setStrokingColor(color(border.color()));
        float[] dash = switch (border.lineStyle()) {
            case DASHED -> new float[]{width * 3, width * 3};
            case DOTTED -> new float[]{width, width};
            default -> new float[0];
        };
        stream.setLineDashPattern(dash, 0);
        int lines = border.lineStyle() == BorderLineStyle.DOUBLE ? 2 : 1;
        float stroke = lines == 2 ? width / 3 : width;
        stream.setLineWidth(stroke);
        for (int index = 0; index < lines; index++) {
            float inset = stroke / 2 + index * 2 * stroke;
            stream.moveTo(x1 + inwardX * inset, y1 + inwardY * inset);
            stream.lineTo(x2 + inwardX * inset, y2 + inwardY * inset);
            stream.stroke();
        }
    }

    /** RGB 模型值转换为普通外观使用的设备颜色。 */
    private static PDColor color(DocumentColor color) {
        return new PDColor(new float[]{color.red() / COLOR_CHANNEL_MAX, color.green() / COLOR_CHANNEL_MAX,
                color.blue() / COLOR_CHANNEL_MAX}, PDDeviceRGB.INSTANCE);
    }

    /** 编辑默认样式使用与外观一致的十六进制颜色。 */
    private static String hex(DocumentColor color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.red(), color.green(), color.blue());
    }
}
