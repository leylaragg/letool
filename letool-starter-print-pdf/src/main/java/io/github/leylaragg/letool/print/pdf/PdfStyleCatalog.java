package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.BoxSpacing;
import io.github.leylaragg.letool.print.document.style.CellBorder;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextDecoration;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把命名文档样式编译为框架控制的 CSS 类名和声明。
 *
 * @author leyland
 */
final class PdfStyleCatalog {

    /** 文本样式名到安全类名的映射。 */
    private final Map<String, String> textClasses;

    /** 段落样式名到安全类名的映射。 */
    private final Map<String, String> paragraphClasses;

    /** 表格样式名到安全类名的映射。 */
    private final Map<String, String> tableClasses;

    /** 单元格样式名到安全类名的映射。 */
    private final Map<String, String> cellClasses;

    /** 表格样式对应的固定列宽 CSS 值。 */
    private final Map<String, List<String>> tableColumnWidths;

    /** 完整且不含模板样式名的 CSS。 */
    private final String css;

    /** 保存一次编译得到的不可变目录。 */
    private PdfStyleCatalog(Compiler compiler) {
        this.textClasses = Map.copyOf(compiler.textClasses);
        this.paragraphClasses = Map.copyOf(compiler.paragraphClasses);
        this.tableClasses = Map.copyOf(compiler.tableClasses);
        this.cellClasses = Map.copyOf(compiler.cellClasses);
        this.tableColumnWidths = Map.copyOf(compiler.tableColumnWidths);
        this.css = compiler.css.toString();
    }

    /**
     * 编译命名样式，并在生成 CSS 前核对字体面。
     *
     * @param styleSheet 文档命名样式表
     * @param fontCatalog PDF 字体目录
     * @return 可并发复用的样式目录
     */
    static PdfStyleCatalog compile(StyleSheet styleSheet, PdfFontCatalog fontCatalog) {
        return new PdfStyleCatalog(new Compiler(styleSheet, fontCatalog).compile());
    }

    /** @return 指定文本样式的安全类名 */
    String textClass(String styleName) {
        return requireClass(textClasses, styleName, "文本");
    }

    /** @return 指定段落样式的安全类名 */
    String paragraphClass(String styleName) {
        return requireClass(paragraphClasses, styleName, "段落");
    }

    /** @return 指定表格样式的安全类名 */
    String tableClass(String styleName) {
        return requireClass(tableClasses, styleName, "表格");
    }

    /** @return 指定单元格样式的安全类名 */
    String cellClass(String styleName) {
        return requireClass(cellClasses, styleName, "单元格");
    }

    /** @return 指定表格样式的固定列宽 CSS 值 */
    List<String> tableColumnWidths(String styleName) {
        List<String> widths = tableColumnWidths.get(styleName);
        if (widths == null) {
            throw PrintValidationException.invalidDocument("PDF 表格样式目录不存在");
        }
        return widths;
    }

    /** @return 已完成安全编译的 CSS */
    String css() {
        return css;
    }

    /** 空引用不添加类名，非空引用必须来自已编译样式表。 */
    private String requireClass(Map<String, String> classes, String styleName, String type) {
        if (styleName == null || styleName.isEmpty()) {
            return "";
        }
        String className = classes.get(styleName);
        if (className == null) {
            throw PrintValidationException.invalidDocument("PDF " + type + "样式目录不存在");
        }
        return className;
    }

    /** 单次样式编译使用的局部状态。 */
    private static final class Compiler {

        /** 待编译的样式表。 */
        private final StyleSheet styles;

        /** 用于核对字体族和字重的目录。 */
        private final PdfFontCatalog fonts;

        /** 各类型安全类名。 */
        private final Map<String, String> textClasses = new LinkedHashMap<>();
        private final Map<String, String> paragraphClasses = new LinkedHashMap<>();
        private final Map<String, String> tableClasses = new LinkedHashMap<>();
        private final Map<String, String> cellClasses = new LinkedHashMap<>();

        /** 固定表格列宽在 XHTML 写出时复用。 */
        private final Map<String, List<String>> tableColumnWidths = new LinkedHashMap<>();

        /** 顺序稳定的 CSS 输出。 */
        private final StringBuilder css = new StringBuilder(4_096);

        /** 保存本次编译依赖。 */
        private Compiler(StyleSheet styles, PdfFontCatalog fonts) {
            this.styles = Objects.requireNonNull(styles, "styles 不能为空");
            this.fonts = Objects.requireNonNull(fonts, "fonts 不能为空");
        }

        /** 按类型和名称稳定编译全部命名样式。 */
        private Compiler compile() {
            compileTextStyles();
            compileParagraphStyles();
            compileTableStyles();
            compileCellStyles();
            return this;
        }

        /** 文本样式决定字体面和文字外观。 */
        private void compileTextStyles() {
            int index = 0;
            for (Map.Entry<String, TextStyle> entry : sorted(styles.textStyles())) {
                String className = "lt-text-" + index++;
                textClasses.put(entry.getKey(), className);
                css.append('.').append(className).append('{');
                appendText(css, entry.getValue());
                css.append('}');
            }
        }

        /** 段落样式同时携带其默认文本样式，供未显式设样式的文本继承。 */
        private void compileParagraphStyles() {
            int index = 0;
            for (Map.Entry<String, ParagraphStyle> entry : sorted(styles.paragraphStyles())) {
                String className = "lt-paragraph-" + index++;
                paragraphClasses.put(entry.getKey(), className);
                ParagraphStyle style = entry.getValue();
                css.append('.').append(className).append('{');
                appendText(css, styles.resolveText(style.textStyleName()));
                appendParagraph(css, style);
                css.append('}');
            }
        }

        /** 表格样式保存布局声明，并把列宽留给 colgroup 使用。 */
        private void compileTableStyles() {
            int index = 0;
            for (Map.Entry<String, TableStyle> entry : sorted(styles.tableStyles())) {
                String className = "lt-table-" + index++;
                tableClasses.put(entry.getKey(), className);
                TableStyle style = entry.getValue();
                tableColumnWidths.put(entry.getKey(), style.columnWidths().stream()
                        .map(PdfCssValues::length).toList());
                css.append('.').append(className).append('{');
                appendProperty(css, "width", PdfCssValues.length(style.width()));
                appendProperty(css, "table-layout", PdfCssValues.tableLayout(style.layoutMode()));
                if (style.pageBreakPolicy() == TablePageBreakPolicy.KEEP_TABLE) {
                    appendProperty(css, "page-break-inside", "avoid");
                }
                css.append('}');
                if (style.repeatHeader()) {
                    css.append('.').append(className)
                            .append(">thead{display:table-header-group;}");
                }
                if (style.pageBreakPolicy() == TablePageBreakPolicy.KEEP_ROWS) {
                    css.append('.').append(className)
                            .append(" tr{page-break-inside:avoid;}");
                }
            }
        }

        /** 单元格样式逐边输出边框，并保留独立内边距。 */
        private void compileCellStyles() {
            int index = 0;
            for (Map.Entry<String, CellStyle> entry : sorted(styles.cellStyles())) {
                String className = "lt-cell-" + index++;
                cellClasses.put(entry.getKey(), className);
                CellStyle style = entry.getValue();
                css.append('.').append(className).append('{');
                appendBorder(css, "top", style.topBorder());
                appendBorder(css, "right", style.rightBorder());
                appendBorder(css, "bottom", style.bottomBorder());
                appendBorder(css, "left", style.leftBorder());
                style.background().ifPresent(color -> appendProperty(
                        css, "background-color", PdfCssValues.color(color)));
                appendPadding(css, style.padding());
                appendProperty(css, "vertical-align",
                        PdfCssValues.verticalAlignment(style.verticalAlignment()));
                css.append('}');
            }
        }

        /** 写入一组完整文本属性，并先验证对应字体面。 */
        private void appendText(StringBuilder output, TextStyle style) {
            String family = resolveFontFamily(style.fontFamily(), style.fontWeight());
            appendProperty(output, "font-family", family);
            appendProperty(output, "font-size", PdfCssValues.length(style.fontSize()));
            appendProperty(output, "font-weight", PdfCssValues.fontWeight(style.fontWeight()));
            appendProperty(output, "color", PdfCssValues.color(style.color()));
            appendProperty(output, "line-height", PdfCssValues.decimal(style.lineHeight()));
            List<TextDecoration> decorations = new ArrayList<>(style.decorations());
            decorations.sort(Comparator.comparingInt(Enum::ordinal));
            String decoration = decorations.isEmpty() ? "none" : decorations.stream()
                    .map(value -> value == TextDecoration.UNDERLINE
                            ? "underline" : "line-through")
                    .reduce((left, right) -> left + " " + right).orElse("none");
            appendProperty(output, "text-decoration", decoration);
        }

        /** 显式字体族必须完全匹配；空字体族使用唯一回退族。 */
        private String resolveFontFamily(String family, FontWeight weight) {
            if (!family.isEmpty()) {
                return quote(fonts.requireFace(family, weight).familyName());
            }
            if (fonts.fonts().isEmpty()) {
                return "sans-serif";
            }
            PdfFont fallback = fonts.defaultFace(weight).orElseThrow(
                    () -> PrintValidationException.invalidDocument("PDF 默认字体面不存在"));
            return quote(fallback.familyName());
        }

        /** 写入段落缩进、空白、折行和同页偏好。 */
        private void appendParagraph(StringBuilder output, ParagraphStyle style) {
            appendProperty(output, "text-align", PdfCssValues.alignment(style.alignment()));
            appendProperty(output, "text-indent", PdfCssValues.length(style.firstLineIndent()));
            appendProperty(output, "margin-left", PdfCssValues.length(style.leftIndent()));
            appendProperty(output, "margin-right", PdfCssValues.length(style.rightIndent()));
            appendProperty(output, "margin-top", PdfCssValues.length(style.spacingBefore()));
            appendProperty(output, "margin-bottom", PdfCssValues.length(style.spacingAfter()));
            String whiteSpace = switch (style.whitespaceMode()) {
                case COLLAPSE -> "normal";
                case PRESERVE_LINE_BREAKS -> "pre-line";
                case PRESERVE_ALL -> "pre-wrap";
            };
            if (style.textWrapMode() == TextWrapMode.NO_WRAP) {
                whiteSpace = "nowrap";
            }
            appendProperty(output, "white-space", whiteSpace);
            appendProperty(output, "overflow-wrap",
                    style.textWrapMode() == TextWrapMode.BREAK_LONG_WORDS
                            ? "break-word" : "normal");
            appendProperty(output, "page-break-inside",
                    style.keepTogether() ? "avoid" : "auto");
        }

        /** 写入一侧边框。 */
        private void appendBorder(StringBuilder output, String side, CellBorder border) {
            if (border.lineStyle() == io.github.leylaragg.letool.print.document.style.BorderLineStyle.NONE) {
                appendProperty(output, "border-" + side, "none");
                return;
            }
            String value = PdfCssValues.length(border.width()) + " "
                    + PdfCssValues.borderStyle(border.lineStyle()) + " "
                    + PdfCssValues.color(border.color());
            appendProperty(output, "border-" + side, value);
        }

        /** 四边内边距按 CSS 上右下左顺序输出。 */
        private void appendPadding(StringBuilder output, BoxSpacing padding) {
            appendProperty(output, "padding", PdfCssValues.length(padding.top()) + " "
                    + PdfCssValues.length(padding.right()) + " "
                    + PdfCssValues.length(padding.bottom()) + " "
                    + PdfCssValues.length(padding.left()));
        }

        /** 样式名称不进入 CSS，因此字体族只接收 PdfFont 已校验的值。 */
        private String quote(String family) {
            return "'" + family + "'";
        }

        /** 追加一条紧凑 CSS 属性。 */
        private void appendProperty(StringBuilder output, String name, String value) {
            output.append(name).append(':').append(value).append(';');
        }

        /** Map 的内部实现不影响输出顺序，统一按样式名排序。 */
        private <T> List<Map.Entry<String, T>> sorted(Map<String, T> source) {
            return source.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
        }
    }
}
