package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表格严格网格校验测试。
 *
 * @author leyland
 */
class TableGridValidationTest {

    /** 验证普通矩形表格使用首行固定列数。 */
    @Test
    void shouldAcceptRectangularGrid() {
        TableNode table = new TableNode("rectangular", 0, List.of(
                row(cell(1, 1), cell(1, 1)),
                row(cell(1, 1), cell(1, 1))));

        assertThat(table.rows()).hasSize(2);
    }

    /** 验证跨行单元格占位后，后续行可以只声明剩余列。 */
    @Test
    void shouldAcceptValidRowSpanGrid() {
        TableNode table = new TableNode("inventory", 0, List.of(
                row(cell(2, 1), cell(1, 1)),
                row(cell(1, 1))));

        assertThat(table.rows()).hasSize(2);
    }

    /** 验证跨行占位会阻止后续单元格覆盖同一列。 */
    @Test
    void shouldRejectCellOverlappingPreviousRowSpan() {
        assertThatThrownBy(() -> new TableNode("inventory", 0, List.of(
                row(cell(2, 1), cell(1, 1)),
                row(cell(1, 2)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("网格");
    }

    /** 验证行填充不足和超出固定列数均失败。 */
    @Test
    void shouldRejectMissingAndExcessiveColumns() {
        assertThatThrownBy(() -> new TableNode("missing", 0, List.of(
                row(cell(1, 1), cell(1, 1)), row(cell(1, 1)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("网格");
        assertThatThrownBy(() -> new TableNode("excessive", 0, List.of(
                row(cell(1, 1)), row(cell(1, 1), cell(1, 1)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("网格");
    }

    /** 验证列跨度不能穿过前序跨行单元格占据的中间列。 */
    @Test
    void shouldRejectColSpanCrossingOccupiedMiddleColumn() {
        assertThatThrownBy(() -> new TableNode("crossing", 0, List.of(
                row(cell(1, 1), cell(2, 1), cell(1, 1)),
                row(cell(1, 2)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("网格");
    }

    /** 验证跨行范围不能超过表格最后一行。 */
    @Test
    void shouldRejectUnclosedBodyRowSpan() {
        assertThatThrownBy(() -> new TableNode("inventory", 0, List.of(
                row(cell(2, 1), cell(1, 1)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("跨行");
    }

    /** 验证表头跨行范围不能进入表体分区。 */
    @Test
    void shouldRejectHeaderRowSpanCrossingIntoBody() {
        assertThatThrownBy(() -> new TableNode("inventory", 1, List.of(
                row(cell(2, 1), cell(1, 1)),
                row(cell(1, 1), cell(1, 1)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("表头");
    }

    /** 验证没有表体的纯表头仍然是合法表格。 */
    @Test
    void shouldAcceptHeaderOnlyTable() {
        TableNode table = new TableNode("inventory", 1, List.of(
                row(cell(1, 1), cell(1, 1))));

        assertThat(table.headerRowCount()).isEqualTo(1);
    }

    /** 验证超大有效列数在分配网格占位数组前被拒绝。 */
    @Test
    void shouldRejectExcessiveEffectiveColumns() {
        assertThatThrownBy(() -> new TableNode("wide", 0, List.of(row(
                cell(1, 1_000), cell(1, 1_000), cell(1, 1_000), cell(1, 1_000),
                cell(1, 1_000), cell(1, 1_000), cell(1, 1_000), cell(1, 1_000),
                cell(1, 1_000), cell(1, 1_000), cell(1, 1_000)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("列数");
    }

    /** 验证表头行数不能超出实际行范围。 */
    @Test
    void shouldRejectHeaderRowCountOutOfRange() {
        assertThatThrownBy(() -> new TableNode("header", 2, List.of(row(cell(1, 1)))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("表头行数");
    }

    /** 创建仅包含结构信息的单元格。 */
    private static TableCell cell(int rowSpan, int colSpan) {
        return new TableCell(List.of(), rowSpan, colSpan);
    }

    /** 创建表格行。 */
    private static TableRow row(TableCell... cells) {
        return new TableRow(List.of(cells));
    }
}
