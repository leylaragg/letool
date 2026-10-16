package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.bidi.SimpleBidiReorderer;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.pdfboxout.FontRun;
import com.openhtmltopdf.pdfboxout.PdfBoxFSFont;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfBoxTextRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineLayoutBox;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从原生字体解析器取得真实字体片段，共用其字宽测量完整换行和最终批注外观。
 *
 * @author leyland
 */
final class PdfFreeTextLayout {

    /** 物理长度与字体内部单位的换算比例。 */
    private static final float POINTS_PER_MILLIMETER = 72F / 25.4F;
    private static final float FONT_UNITS = 1000F;
    private static final float WIDTH_TOLERANCE = 0.01F;

    private PdfFreeTextLayout() {
    }

    /** 同一真实字体连续绘制的一段文字。 */
    record TextRun(PdfFont font, String text) {
    }

    /** 一条已经按实际字宽换行的完整文字行。 */
    record TextLine(List<TextRun> runs) {
    }

    /** 单个字形的源字体与宽度；换行符只保留文字，不引用字体。 */
    private record Glyph(PdfFont font, String text, float width) {
    }

    /** 原生回退选择结果与全部字形的最大升部、降部。 */
    private record TextMetrics(List<Glyph> glyphs, float ascent, float descent) {
    }

    /** 尺寸和外观输入均脱离测量器生命周期，不保存 PDF 文档所属对象。 */
    record MeasuredBox(String content, String author, TextStyle textStyle, CellStyle boxStyle,
            DocumentColor background, PdfFont font, List<TextLine> lines, float width, float height,
            float textLeft, float firstBaselineFromTop, float lineAdvance) {
    }

    /** 原生流视图与其文本框外观快照。 */
    record Prepared(String xhtml, Map<String, MeasuredBox> boxes) {
    }

    /** 在每个实际父容器内测量文字；运行页眉和页脚需要显式纳入布局树。 */
    static Prepared prepare(PdfBoxRenderer renderer, String original, StyleSheet styles,
            DocumentOptions options, PdfFontCatalog catalog) throws IOException {
        Map<Element, Box> boxes = new IdentityHashMap<>();
        collect(renderer.getRootBox(), boxes);
        collect(renderer.getSharedContext().getBoxById(PdfXhtmlRenderer.HEADER_REGION_ID), boxes);
        collect(renderer.getSharedContext().getBoxById(PdfXhtmlRenderer.FOOTER_REGION_ID), boxes);
        Map<String, PdfFont> sources = fontSources(catalog);
        Map<String, MeasuredBox> measured = new LinkedHashMap<>();
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        for (Map.Entry<Element, Box> entry : boxes.entrySet()) {
            Element element = entry.getKey();
            if (!"letool-free-text".equals(element.getAttribute("type"))) {
                continue;
            }
            TextStyle text = styles.resolveText(element.getAttribute("data-text-style"));
            CellStyle box = styles.resolveCell(element.getAttribute("data-box-style"));
            PdfFont source = selectFont(text, options, catalog);
            PdfBoxFSFont nativeFont = (PdfBoxFSFont) entry.getValue().getStyle().getFSFont(context);
            boolean inline = Boolean.parseBoolean(element.getAttribute("data-inline"));
            float available = availableWidth(entry.getValue(), inline) / renderer.getDotsPerPoint();
            MeasuredBox value = measure(element, text, box, source, nativeFont, sources, available,
                    renderer.getDotsPerPoint());
            measured.put(element.getAttribute("data-free-text-key"), value);
            element.setAttribute("data-ready", "true");
            element.setAttribute("style", "display:" + (inline ? "inline-block" : "block")
                    + ";width:" + value.width() + "pt;height:" + value.height()
                    + "pt;page-break-inside:avoid;vertical-align:middle;");
            element.setTextContent("");
        }
        return measured.isEmpty() ? new Prepared(original, Map.of())
                : new Prepared(PdfNativeFragmenter.serialize(renderer.getRootBox().getElement().getOwnerDocument()),
                        Map.copyOf(measured));
    }

    /** 自动宽组合先扣除原生测得的同组间隔，再以外层实际宽度约束完整文本框。 */
    private static float availableWidth(Box probe, boolean inline) {
        if (!inline) {
            return probe.getContentWidth();
        }
        float reserved = 0;
        Box branch = probe;
        Box parent = probe.getContainingBlock();
        while (parent.getElement() != null
                && "true".equals(parent.getElement().getAttribute("data-auto-group"))) {
            reserved += Math.max(0, parent.getContentWidth() - branch.getWidth());
            branch = parent;
            parent = parent.getContainingBlock();
        }
        return parent.getContentWidth() - reserved;
    }

    /** 用真实 PostScript 身份将原生字体片段映射回可重新打开的宿主字体源。 */
    private static Map<String, PdfFont> fontSources(PdfFontCatalog catalog) throws IOException {
        Map<String, PdfFont> sources = new LinkedHashMap<>();
        for (PdfFont source : catalog.fonts()) {
            sources.putIfAbsent(PdfFontSource.postScriptName(PdfFontSource.read(source)), source);
        }
        return sources;
    }

    /** 默认编辑字体仍由文本样式决定，中文等缺字只在实际片段中回退。 */
    private static PdfFont selectFont(TextStyle style, DocumentOptions options, PdfFontCatalog catalog) {
        String family = style.fontFamily().isEmpty() ? options.defaultFontFamily() : style.fontFamily();
        if (!family.isEmpty()) {
            return catalog.requireFace(family, style.fontWeight(), style.fontPosture());
        }
        return catalog.defaultFace(style.fontWeight()).orElseThrow(
                () -> PrintValidationException.invalidDocument("流式文本框需要宿主字体"));
    }

    /** 将边框、内边距与完整字体行合并为不可分割的物理尺寸。 */
    private static MeasuredBox measure(Element element, TextStyle text, CellStyle box, PdfFont source,
            PdfBoxFSFont nativeFont, Map<String, PdfFont> sources, float available, float dots) throws IOException {
        float left = points(box.padding().left()) + points(box.leftBorder().width());
        float right = points(box.padding().right()) + points(box.rightBorder().width());
        float top = points(box.padding().top()) + points(box.topBorder().width());
        float bottom = points(box.padding().bottom()) + points(box.bottomBorder().width());
        String content = element.getTextContent();
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        float size = (float) text.fontSize().value();
        TextMetrics metrics = metrics(normalized, nativeFont, sources, size);
        // 自然宽度向上取到引擎坐标格，父容器上限向下取整，避免舍入让末字另起一行。
        float width = (float) Math.floor(available * dots) / dots;
        if ("FIT_CONTENT".equals(element.getAttribute("data-width-mode"))) {
            float natural = naturalWidth(metrics.glyphs()) + left + right;
            width = Math.min(width, (float) Math.ceil(natural * dots) / dots);
        }
        if (width <= left + right) {
            throw PrintValidationException.invalidDocument("完整文本框没有可用文字宽度");
        }
        List<TextLine> lines = wrap(metrics.glyphs(), width - left - right);
        float advance = size * (float) text.lineHeight();
        float firstLine = Math.max(advance, Math.max(size, metrics.ascent() + metrics.descent()));
        float baseline = top + (firstLine - metrics.ascent() - metrics.descent()) / 2 + metrics.ascent();
        float height = top + bottom + firstLine + (lines.size() - 1) * advance;
        height = (float) Math.ceil(height * dots) / dots;
        DocumentColor background = box.background().orElse(null);
        if (element.hasAttribute("data-background")) {
            String[] channels = element.getAttribute("data-background").split(",");
            background = DocumentColor.rgb(Integer.parseInt(channels[0]), Integer.parseInt(channels[1]),
                    Integer.parseInt(channels[2]));
        }
        return new MeasuredBox(content, element.getAttribute("data-author"), text, box, background,
                source, lines, width, height, left, baseline, advance);
    }

    /** 复用原生字体回退算法，拒绝缺字替换，完整保存最终外观使用的字体片段。 */
    private static TextMetrics metrics(String content, PdfBoxFSFont nativeFont,
            Map<String, PdfFont> sources, float size) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        float ascent = 0;
        float descent = 0;
        String[] paragraphs = content.split("\n", -1);
        for (int index = 0; index < paragraphs.length; index++) {
            if (index > 0) {
                glyphs.add(new Glyph(null, "\n", 0));
            }
            StringBuilder actual = new StringBuilder();
            List<FontRun> runs = PdfBoxTextRenderer.divideIntoFontRuns(nativeFont, paragraphs[index],
                    new SimpleBidiReorderer());
            for (FontRun run : runs) {
                PDFont font = run.description.getFont();
                PdfFont source = sources.get(font.getName().replaceFirst("^[A-Z]{6}\\+", ""));
                if (source == null) {
                    throw PrintValidationException.invalidDocument("文本框字体片段无法定位到宿主字体源");
                }
                actual.append(run.string);
                ascent = Math.max(ascent, font.getFontDescriptor().getAscent() * size / FONT_UNITS);
                descent = Math.max(descent, -font.getFontDescriptor().getDescent() * size / FONT_UNITS);
                for (int offset = 0; offset < run.string.length();) {
                    int codePoint = run.string.codePointAt(offset);
                    offset += Character.charCount(codePoint);
                    String character = new String(Character.toChars(codePoint));
                    glyphs.add(new Glyph(source, character, font.getStringWidth(character) * size / FONT_UNITS));
                }
            }
            if (!actual.toString().equals(paragraphs[index])) {
                throw PrintValidationException.invalidDocument("文本框字体不能完整表示原始正文");
            }
        }
        return new TextMetrics(List.copyOf(glyphs), ascent, descent);
    }

    /** 不折行时，以最长显式文字行决定收窄宽度。 */
    private static float naturalWidth(List<Glyph> glyphs) {
        float maximum = 0;
        float current = 0;
        for (Glyph glyph : glyphs) {
            if (glyph.font() == null) {
                maximum = Math.max(maximum, current);
                current = 0;
            } else {
                current += glyph.width();
            }
        }
        return Math.max(maximum, current);
    }

    /** 按实际字宽逐字换行，保留所有空行和尾部文字，不设置最大行数。 */
    private static List<TextLine> wrap(List<Glyph> glyphs, float available) {
        List<TextLine> lines = new ArrayList<>();
        List<Glyph> line = new ArrayList<>();
        float used = 0;
        for (Glyph glyph : glyphs) {
            if (glyph.font() == null) {
                lines.add(line(line));
                line.clear();
                used = 0;
                continue;
            }
            if (glyph.width() > available + WIDTH_TOLERANCE) {
                throw PrintValidationException.invalidDocument("完整文本框无法容纳单个字形");
            }
            if (!line.isEmpty() && used + glyph.width() > available + WIDTH_TOLERANCE) {
                lines.add(line(line));
                line.clear();
                used = 0;
            }
            line.add(glyph);
            used += glyph.width();
        }
        lines.add(line(line));
        return List.copyOf(lines);
    }

    /** 同一行的相邻同字体字形合并，测量与绘制共用此不可变结果。 */
    private static TextLine line(List<Glyph> glyphs) {
        List<TextRun> runs = new ArrayList<>();
        PdfFont current = null;
        StringBuilder text = new StringBuilder();
        for (Glyph glyph : glyphs) {
            if (current != glyph.font() && !text.isEmpty()) {
                runs.add(new TextRun(current, text.toString()));
                text.setLength(0);
            }
            current = glyph.font();
            text.append(glyph.text());
        }
        if (!text.isEmpty()) {
            runs.add(new TextRun(current, text.toString()));
        }
        return new TextLine(List.copyOf(runs));
    }

    /** 样式物理长度统一换算为 PDF 点。 */
    static float points(DocumentLength length) {
        return (float) length.value() * (length.unit() == DocumentLength.Unit.MILLIMETER
                ? POINTS_PER_MILLIMETER : 1);
    }

    /** 通过元素身份取得替换对象和其真实包含块，逻辑 ID 不用于去重物理框。 */
    private static void collect(Box box, Map<Element, Box> boxes) {
        if (box == null) {
            return;
        }
        if (box.getElement() != null) {
            boxes.putIfAbsent(box.getElement(), box);
        }
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof Box nested) {
                    collect(nested, boxes);
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                collect(child, boxes);
            }
        }
    }
}
