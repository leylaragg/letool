package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 命名样式及其基础值的不可变契约测试。
 *
 * @author leyland
 */
class StyleSheetTest {

    /** 验证空样式表仍提供一套由框架定义的稳定默认值。 */
    @Test
    void shouldExposeFrameworkDefaults() {
        StyleSheet styles = StyleSheet.empty();

        assertThat(styles.hasNamedStyles()).isFalse();
        assertThat(styles.defaultTextStyle().fontSize()).isEqualTo(DocumentLength.points(10));
        assertThat(styles.defaultParagraphStyle().whitespaceMode()).isEqualTo(WhitespaceMode.COLLAPSE);
        assertThat(styles.defaultTableStyle().width()).isEqualTo(DocumentLength.percent(100));
        assertThat(styles.defaultCellStyle().verticalAlignment()).isEqualTo(VerticalAlignment.TOP);
    }

    /** 验证四类样式分别注册，并在构建后与调用方集合隔离。 */
    @Test
    void shouldBuildTypedImmutableStyles() {
        List<DocumentLength> columns = new ArrayList<>(List.of(
                DocumentLength.percent(40), DocumentLength.percent(60)));
        TextStyle text = TextStyle.builder()
                .fontFamily("Ailind Sans")
                .fontSize(DocumentLength.points(11))
                .fontWeight(FontWeight.BOLD)
                .color(DocumentColor.rgb(32, 64, 96))
                .lineHeight(1.5)
                .decorations(Set.of(TextDecoration.UNDERLINE))
                .build();
        ParagraphStyle paragraph = ParagraphStyle.builder()
                .textStyleName("body-text")
                .alignment(TextAlignment.JUSTIFY)
                .whitespaceMode(WhitespaceMode.PRESERVE_LINE_BREAKS)
                .textWrapMode(TextWrapMode.BREAK_LONG_WORDS)
                .keepTogether(true)
                .build();
        TableStyle table = TableStyle.builder()
                .layoutMode(TableLayoutMode.FIXED)
                .columnWidths(columns)
                .repeatHeader(true)
                .pageBreakPolicy(TablePageBreakPolicy.KEEP_ROWS)
                .build();
        CellStyle cell = CellStyle.builder()
                .background(DocumentColor.rgb(245, 245, 245))
                .padding(BoxSpacing.all(DocumentLength.millimeters(1.5)))
                .verticalAlignment(VerticalAlignment.MIDDLE)
                .build();

        StyleSheet styles = StyleSheet.builder()
                .text("body-text", text)
                .paragraph("body", paragraph)
                .table("detail-table", table)
                .cell("detail-cell", cell)
                .build();
        columns.clear();

        assertThat(styles.text("body-text")).containsSame(text);
        assertThat(styles.paragraph("body")).containsSame(paragraph);
        assertThat(styles.table("detail-table").orElseThrow().columnWidths()).hasSize(2);
        assertThat(styles.cell("detail-cell")).containsSame(cell);
        assertThatThrownBy(() -> styles.table("detail-table").orElseThrow()
                .columnWidths().add(DocumentLength.percent(10)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 独立构建但内容相同的样式应按值相等，便于宿主缓存和比较模型快照。 */
    @Test
    void shouldCompareStylesByValue() {
        assertThat(TextStyle.builder().build()).isEqualTo(TextStyle.builder().build());
        assertThat(ParagraphStyle.builder().build()).isEqualTo(ParagraphStyle.builder().build());
        assertThat(TableStyle.builder().build()).isEqualTo(TableStyle.builder().build());
        assertThat(CellStyle.builder().build()).isEqualTo(CellStyle.builder().build());
    }

    /** 验证样式名称、引用和数值边界在模型进入渲染链前被拒绝。 */
    @Test
    void shouldRejectInvalidStyleDefinitions() {
        assertThatThrownBy(() -> DocumentLength.points(Double.NaN))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> DocumentColor.rgb(256, 0, 0))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> TextStyle.builder().lineHeight(0).build())
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> TableStyle.builder()
                .layoutMode(TableLayoutMode.FIXED)
                .columnWidths(List.of(DocumentLength.percent(40), DocumentLength.percent(40)))
                .build())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("100");
        assertThatThrownBy(() -> StyleSheet.builder()
                .text("Body", TextStyle.defaults())
                .build())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("样式名称");
        assertThatThrownBy(() -> StyleSheet.builder()
                .paragraph("body", ParagraphStyle.builder()
                        .textStyleName("missing")
                        .build())
                .build())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
    }
}
