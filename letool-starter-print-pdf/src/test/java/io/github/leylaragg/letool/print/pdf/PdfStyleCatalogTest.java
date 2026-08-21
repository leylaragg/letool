package io.github.leylaragg.letool.print.pdf;

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
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextAlignment;
import io.github.leylaragg.letool.print.document.style.TextDecoration;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.VerticalAlignment;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 命名样式的安全类名、CSS 映射和字体校验测试。
 *
 * @author leyland
 */
class PdfStyleCatalogTest {

    /** 四类模板样式只映射为框架类名，原始名称不会进入 CSS。 */
    @Test
    void shouldGenerateStableClassesWithoutUsingTemplateStyleNames() {
        StyleSheet styles = StyleSheet.builder()
                .text("unsafe-text-name", textStyle(FontWeight.BOLD))
                .paragraph("unsafe-paragraph-name", paragraphStyle("unsafe-text-name"))
                .table("unsafe-table-name", tableStyle())
                .cell("unsafe-cell-name", cellStyle())
                .build();

        PdfStyleCatalog catalog = PdfStyleCatalog.compile(styles, fontCatalog());

        assertThat(catalog.textClass("unsafe-text-name")).isEqualTo("lt-text-0");
        assertThat(catalog.paragraphClass("unsafe-paragraph-name")).isEqualTo("lt-paragraph-0");
        assertThat(catalog.tableClass("unsafe-table-name")).isEqualTo("lt-table-0");
        assertThat(catalog.cellClass("unsafe-cell-name")).isEqualTo("lt-cell-0");
        assertThat(catalog.css()).contains(".lt-text-0{")
                .contains(".lt-paragraph-0{")
                .contains(".lt-table-0{")
                .contains(".lt-cell-0{")
                .doesNotContain("unsafe-text-name")
                .doesNotContain("unsafe-paragraph-name")
                .doesNotContain("unsafe-table-name")
                .doesNotContain("unsafe-cell-name");
    }

    /** 强类型样式会得到完整且固定格式的 CSS 值。 */
    @Test
    void shouldCompileStronglyTypedStyleValues() {
        PdfStyleCatalog catalog = PdfStyleCatalog.compile(StyleSheet.builder()
                .text("title", textStyle(FontWeight.BOLD))
                .paragraph("body", paragraphStyle("title"))
                .table("grid", tableStyle())
                .cell("amount", cellStyle())
                .build(), fontCatalog());

        assertThat(catalog.css())
                .contains("font-family:'Droid Sans Fallback'")
                .contains("font-size:12.5pt")
                .contains("font-weight:700")
                .contains("color:#0C2238")
                .contains("line-height:1.35")
                .contains("text-decoration:underline line-through")
                .contains("text-align:justify")
                .contains("text-indent:6.5mm")
                .contains("white-space:pre-wrap")
                .contains("overflow-wrap:break-word")
                .contains("table-layout:fixed")
                .contains("width:100%")
                .contains("page-break-inside:avoid")
                .contains("border-top:0.5pt solid #46505A")
                .contains("background-color:#F0F1F2")
                .contains("padding:1mm 2mm 3mm 4mm")
                .contains("vertical-align:middle");
        assertThat(catalog.tableColumnWidths("grid")).containsExactly("30%", "70%");
    }

    /** CSS 数值不受 JVM 默认区域设置影响。 */
    @Test
    void shouldKeepDecimalFormattingIndependentFromLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String german = PdfStyleCatalog.compile(StyleSheet.builder()
                    .text("title", textStyle(FontWeight.BOLD)).build(), fontCatalog()).css();
            Locale.setDefault(Locale.US);
            String english = PdfStyleCatalog.compile(StyleSheet.builder()
                    .text("title", textStyle(FontWeight.BOLD)).build(), fontCatalog()).css();

            assertThat(german).isEqualTo(english).contains("12.5pt").doesNotContain("12,5pt");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 显式字体族必须存在完全匹配的字重，不能静默替换。 */
    @Test
    void shouldRejectMissingExplicitFontFace() {
        StyleSheet styles = StyleSheet.builder()
                .text("title", textStyle(FontWeight.BOLD)).build();
        PdfFont normal = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL,
                () -> new ByteArrayInputStream(new byte[]{1}), true);

        assertThatThrownBy(() -> PdfStyleCatalog.compile(
                styles, PdfFontCatalog.of(List.of(normal))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("字体面不存在");
    }

    /** 创建包含颜色、字重和修饰的文本样式。 */
    private static TextStyle textStyle(FontWeight weight) {
        return TextStyle.builder()
                .fontFamily("Droid Sans Fallback")
                .fontSize(DocumentLength.points(12.5))
                .fontWeight(weight)
                .color(DocumentColor.rgb(12, 34, 56))
                .lineHeight(1.35)
                .decorations(Set.of(TextDecoration.UNDERLINE, TextDecoration.LINE_THROUGH))
                .build();
    }

    /** 创建引用默认文本样式的段落排版。 */
    private static ParagraphStyle paragraphStyle(String textStyleName) {
        return ParagraphStyle.builder()
                .textStyleName(textStyleName)
                .alignment(TextAlignment.JUSTIFY)
                .firstLineIndent(DocumentLength.millimeters(6.5))
                .leftIndent(DocumentLength.millimeters(1))
                .rightIndent(DocumentLength.millimeters(2))
                .spacingBefore(DocumentLength.millimeters(3))
                .spacingAfter(DocumentLength.millimeters(4))
                .whitespaceMode(WhitespaceMode.PRESERVE_ALL)
                .textWrapMode(TextWrapMode.BREAK_LONG_WORDS)
                .keepTogether(true)
                .build();
    }

    /** 创建固定列宽并优先整表同页的表格样式。 */
    private static TableStyle tableStyle() {
        return TableStyle.builder()
                .width(DocumentLength.percent(100))
                .layoutMode(TableLayoutMode.FIXED)
                .columnWidths(List.of(
                        DocumentLength.percent(30), DocumentLength.percent(70)))
                .repeatHeader(true)
                .pageBreakPolicy(TablePageBreakPolicy.KEEP_TABLE)
                .build();
    }

    /** 创建带边框、背景和四边间距的单元格样式。 */
    private static CellStyle cellStyle() {
        CellBorder border = CellBorder.of(BorderLineStyle.SOLID,
                DocumentLength.points(0.5), DocumentColor.rgb(70, 80, 90));
        return CellStyle.builder()
                .borders(border)
                .background(DocumentColor.rgb(240, 241, 242))
                .padding(new BoxSpacing(
                        DocumentLength.millimeters(1), DocumentLength.millimeters(2),
                        DocumentLength.millimeters(3), DocumentLength.millimeters(4)))
                .verticalAlignment(VerticalAlignment.MIDDLE)
                .build();
    }

    /** 创建支持常规和粗体的测试字体目录。 */
    private static PdfFontCatalog fontCatalog() {
        return PdfFontCatalog.of(List.of(
                font(FontWeight.NORMAL), font(FontWeight.BOLD)));
    }

    /** 创建不打开真实资源的字体面。 */
    private static PdfFont font(FontWeight weight) {
        return new PdfFont("Droid Sans Fallback", weight,
                () -> new ByteArrayInputStream(new byte[]{1}), true);
    }
}
