package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.BoxSpacing;
import io.github.leylaragg.letool.print.document.style.CellBorder;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextAlignment;
import io.github.leylaragg.letool.print.document.style.TextDecoration;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.VerticalAlignment;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 将 XML 样式声明编译为核心强类型样式表。
 *
 * @author leyland
 */
final class XmlStyleCompiler {

    /** 工具类不允许实例化。 */
    private XmlStyleCompiler() {
    }

    /**
     * 编译可选 styles 节点。
     *
     * @param templateCode 当前模板代码
     * @param styles styles 节点；为空表示没有命名样式
     * @return 已完成内部引用校验的样式表
     */
    static StyleSheet compile(String templateCode, CompiledXmlNode styles) {
        if (styles == null) {
            return StyleSheet.empty();
        }
        StyleSheet.Builder sheet = StyleSheet.builder();
        try {
            for (CompiledXmlNode child : styles.children()) {
                String name = child.attributes().get("name");
                switch (child.name()) {
                    case "text-style" -> sheet.text(name, text(templateCode, child));
                    case "paragraph-style" -> sheet.paragraph(
                            name, paragraph(templateCode, child));
                    case "table-style" -> sheet.table(name, table(templateCode, child));
                    case "cell-style" -> sheet.cell(name, cell(templateCode, child));
                    default -> throw invalid(templateCode, child, "未知样式类型");
                }
            }
            return sheet.build();
        } catch (PrintCompilationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid(templateCode, styles, "样式声明无效", exception);
        }
    }

    /** 编译文本外观。 */
    private static TextStyle text(String templateCode, CompiledXmlNode node) {
        TextStyle.Builder style = TextStyle.builder();
        optional(node, "font-family", style::fontFamily);
        optional(node, "font-size", value -> style.fontSize(
                requireUnit(XmlValueParser.length(value, templateCode, node, "font-size"),
                        DocumentLength.Unit.POINT, templateCode, node, "font-size")));
        optional(node, "font-weight", value -> style.fontWeight(XmlValueParser.enumValue(
                FontWeight.class, value, templateCode, node, "font-weight")));
        optional(node, "color", value -> style.color(
                XmlValueParser.color(value, templateCode, node, "color")));
        optional(node, "line-height", value -> style.lineHeight(
                decimal(value, templateCode, node, "line-height")));
        optional(node, "decorations", value -> style.decorations(
                decorations(value, templateCode, node)));
        return style.build();
    }

    /** 编译段落排版和文字流规则。 */
    private static ParagraphStyle paragraph(String templateCode, CompiledXmlNode node) {
        ParagraphStyle.Builder style = ParagraphStyle.builder();
        optional(node, "text-style", style::textStyleName);
        optional(node, "alignment", value -> style.alignment(XmlValueParser.enumValue(
                TextAlignment.class, value, templateCode, node, "alignment")));
        optional(node, "first-line-indent", value -> style.firstLineIndent(
                millimeters(value, templateCode, node, "first-line-indent")));
        optional(node, "left-indent", value -> style.leftIndent(
                millimeters(value, templateCode, node, "left-indent")));
        optional(node, "right-indent", value -> style.rightIndent(
                millimeters(value, templateCode, node, "right-indent")));
        optional(node, "spacing-before", value -> style.spacingBefore(
                millimeters(value, templateCode, node, "spacing-before")));
        optional(node, "spacing-after", value -> style.spacingAfter(
                millimeters(value, templateCode, node, "spacing-after")));
        optional(node, "whitespace", value -> style.whitespaceMode(XmlValueParser.enumValue(
                WhitespaceMode.class, value, templateCode, node, "whitespace")));
        optional(node, "wrap", value -> style.textWrapMode(XmlValueParser.enumValue(
                TextWrapMode.class, value, templateCode, node, "wrap")));
        optional(node, "keep-together", value -> style.keepTogether(
                XmlValueParser.bool(value, templateCode, node, "keep-together")));
        return style.build();
    }

    /** 编译表格宽度、列布局和跨页策略。 */
    private static TableStyle table(String templateCode, CompiledXmlNode node) {
        TableStyle.Builder style = TableStyle.builder();
        optional(node, "width", value -> style.width(
                XmlValueParser.length(value, templateCode, node, "width")));
        optional(node, "layout", value -> style.layoutMode(XmlValueParser.enumValue(
                TableLayoutMode.class, value, templateCode, node, "layout")));
        optional(node, "column-widths", value -> style.columnWidths(
                columnWidths(value, templateCode, node)));
        optional(node, "repeat-header", value -> style.repeatHeader(
                XmlValueParser.bool(value, templateCode, node, "repeat-header")));
        optional(node, "page-break", value -> style.pageBreakPolicy(XmlValueParser.enumValue(
                TablePageBreakPolicy.class, value, templateCode, node, "page-break")));
        return style.build();
    }

    /** 编译单元格背景、内边距、对齐和边框。 */
    private static CellStyle cell(String templateCode, CompiledXmlNode node) {
        CellStyle.Builder style = CellStyle.builder();
        optional(node, "background", value -> style.background(
                XmlValueParser.color(value, templateCode, node, "background")));
        optional(node, "padding", value -> style.padding(
                padding(value, templateCode, node)));
        optional(node, "vertical-alignment", value -> style.verticalAlignment(
                XmlValueParser.enumValue(VerticalAlignment.class, value,
                        templateCode, node, "vertical-alignment")));
        Set<String> sides = new LinkedHashSet<>();
        for (CompiledXmlNode borderNode : node.children()) {
            String side = borderNode.attributes().get("side").toLowerCase(Locale.ROOT);
            if (!sides.add(side)) {
                throw invalid(templateCode, borderNode, "border.side 不能重复");
            }
            if ("all".equals(side) && sides.size() > 1
                    || sides.contains("all") && !"all".equals(side)) {
                throw invalid(templateCode, borderNode, "all 不能与单边 border 混用");
            }
            CellBorder border = border(templateCode, borderNode);
            switch (side) {
                case "all" -> style.borders(border);
                case "top" -> style.topBorder(border);
                case "right" -> style.rightBorder(border);
                case "bottom" -> style.bottomBorder(border);
                case "left" -> style.leftBorder(border);
                default -> throw invalid(templateCode, borderNode, "border.side 不受支持");
            }
        }
        return style.build();
    }

    /** 编译一条完整边框。 */
    private static CellBorder border(String templateCode, CompiledXmlNode node) {
        BorderLineStyle line = XmlValueParser.enumValue(
                BorderLineStyle.class, node.attributes().get("line"),
                templateCode, node, "border.line");
        DocumentLength width = requireUnit(XmlValueParser.length(
                node.attributes().get("width"), templateCode, node, "border.width"),
                DocumentLength.Unit.POINT, templateCode, node, "border.width");
        return CellBorder.of(line, width, XmlValueParser.color(
                node.attributes().get("color"), templateCode, node, "border.color"));
    }

    /** 解析一个或四个毫米内边距。 */
    private static BoxSpacing padding(String value, String templateCode, CompiledXmlNode node) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) {
            return BoxSpacing.all(millimeters(parts[0], templateCode, node, "padding"));
        }
        if (parts.length == 4) {
            return new BoxSpacing(
                    millimeters(parts[0], templateCode, node, "padding.top"),
                    millimeters(parts[1], templateCode, node, "padding.right"),
                    millimeters(parts[2], templateCode, node, "padding.bottom"),
                    millimeters(parts[3], templateCode, node, "padding.left"));
        }
        throw invalid(templateCode, node, "padding 必须包含一个或四个毫米值");
    }

    /** 解析逗号分隔的列宽。 */
    private static List<DocumentLength> columnWidths(String value, String templateCode, CompiledXmlNode node) {
        String[] parts = value.split(",", -1);
        List<DocumentLength> widths = new ArrayList<>(parts.length);
        for (String part : parts) {
            widths.add(XmlValueParser.length(part.trim(), templateCode, node, "column-widths"));
        }
        return List.copyOf(widths);
    }

    /** 解析去重后的文本修饰。 */
    private static Set<TextDecoration> decorations(
            String value, String templateCode, CompiledXmlNode node) {
        LinkedHashSet<TextDecoration> decorations = new LinkedHashSet<>();
        for (String part : value.split(",", -1)) {
            TextDecoration decoration = XmlValueParser.enumValue(
                    TextDecoration.class, part.trim(), templateCode, node, "decorations");
            if (!decorations.add(decoration)) {
                throw invalid(templateCode, node, "decorations 不能重复");
            }
        }
        return Set.copyOf(decorations);
    }

    /** 属性存在时调用类型化设置函数。 */
    private static void optional(CompiledXmlNode node, String name, Consumer<String> consumer) {
        String value = node.attributes().get(name);
        if (value != null) {
            consumer.accept(value);
        }
    }

    /** 解析有限十进制属性。 */
    private static double decimal(
            String value, String templateCode, CompiledXmlNode node, String property) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw invalid(templateCode, node, property + " 必须为有限数字");
        }
    }

    /** 解析并要求毫米单位。 */
    private static DocumentLength millimeters(String value, String templateCode, CompiledXmlNode node, String property) {
        return requireUnit(XmlValueParser.length(value, templateCode, node, property),
                DocumentLength.Unit.MILLIMETER, templateCode, node, property);
    }

    /** 检查某个核心样式属性只接受指定单位。 */
    private static DocumentLength requireUnit(
            DocumentLength length, DocumentLength.Unit unit,
            String templateCode, CompiledXmlNode node, String property) {
        if (length.unit() != unit) {
            throw invalid(templateCode, node, property + " 的单位不受支持");
        }
        return length;
    }

    /** 创建安全编译异常。 */
    private static PrintCompilationException invalid(String templateCode, CompiledXmlNode node, String detail) {
        return invalid(templateCode, node, detail, null);
    }

    /** 保留核心校验原因链，但不把原异常消息暴露给模板使用者。 */
    private static PrintCompilationException invalid(String templateCode, CompiledXmlNode node, String detail, Throwable cause) {
        String message = templateCode + "：" + node.tagPath() + "，第 " + node.line()
                + " 行，第 " + node.column() + " 列：" + detail;
        return cause == null
                ? PrintCompilationException.invalid(message)
                : PrintCompilationException.invalid(message, cause);
    }
}
