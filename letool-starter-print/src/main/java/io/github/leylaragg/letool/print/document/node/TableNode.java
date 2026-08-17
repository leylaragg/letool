package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 具有可重复表头语义的表格节点。
 *
 * @author leyland
 */
public final class TableNode implements BlockNode {

    /** 单个表格允许的最大有效列数，防止跨度声明放大网格内存。 */
    private static final int MAX_EFFECTIVE_COLUMNS = 10_000;

    /** 表格逻辑 ID。 */
    private final String id;

    /** 表头行数。 */
    private final int headerRowCount;

    /** 不可变表格行。 */
    private final List<TableRow> rows;

    /**
     * 创建并校验表格。
     *
     * @param id 表格逻辑 ID
     * @param headerRowCount 表头行数
     * @param rows 非空表格行
     */
    public TableNode(String id, int headerRowCount, List<TableRow> rows) {
        this.id = NodeValidation.optionalId(id);
        this.rows = List.copyOf(rows);
        if (this.rows.isEmpty()) {
            throw PrintValidationException.invalidDocument("表格至少包含一行");
        }
        if (headerRowCount < 0 || headerRowCount > this.rows.size()) {
            throw PrintValidationException.invalidDocument("表头行数超出表格范围");
        }
        this.headerRowCount = headerRowCount;
        validateGrid();
    }

    /**
     * 校验表头和表体的严格网格结构。
     *
     * <p>表头与表体是相互独立的跨行分区，任何单元格都不能跨越两者边界。</p>
     */
    private void validateGrid() {
        long effectiveColumns = rows.get(0).effectiveColumns();
        if (effectiveColumns > MAX_EFFECTIVE_COLUMNS) {
            throw PrintValidationException.invalidDocument(
                    "表格有效列数不能超过 " + MAX_EFFECTIVE_COLUMNS);
        }
        int columns = (int) effectiveColumns;
        if (headerRowCount > 0) {
            validatePartition(0, headerRowCount, columns, "表头");
        }
        if (headerRowCount < rows.size()) {
            validatePartition(headerRowCount, rows.size(), columns, "表体");
        }
    }

    /**
     * 使用逐列占位状态校验一个连续表格分区。
     *
     * @param startRow 起始行下标，包含该行
     * @param endRow 结束行下标，不包含该行
     * @param columns 表格固定列数
     * @param partitionName 分区名称
     */
    private void validatePartition(int startRow, int endRow, int columns, String partitionName) {
        int[] remainingRowSpans = new int[columns];
        for (int rowIndex = startRow; rowIndex < endRow; rowIndex++) {
            placeRow(rows.get(rowIndex), rowIndex, columns, remainingRowSpans);
            for (int column = 0; column < columns; column++) {
                remainingRowSpans[column]--;
            }
        }
        for (int remainingRowSpan : remainingRowSpans) {
            if (remainingRowSpan > 0) {
                throw PrintValidationException.invalidDocument(
                        partitionName + "存在跨行范围越过分区边界的单元格");
            }
        }
    }

    /**
     * 将一行单元格放入当前网格占位状态。
     *
     * @param row 当前表格行
     * @param rowIndex 当前行下标
     * @param columns 表格固定列数
     * @param remainingRowSpans 各列剩余跨行占位数
     */
    private void placeRow(TableRow row, int rowIndex, int columns, int[] remainingRowSpans) {
        int nextColumn = 0;
        for (TableCell cell : row.cells()) {
            while (nextColumn < columns && remainingRowSpans[nextColumn] > 0) {
                nextColumn++;
            }
            int endColumn = nextColumn + cell.colSpan();
            if (nextColumn >= columns || endColumn > columns) {
                throw invalidGrid(rowIndex);
            }
            for (int column = nextColumn; column < endColumn; column++) {
                if (remainingRowSpans[column] > 0) {
                    throw invalidGrid(rowIndex);
                }
                remainingRowSpans[column] = cell.rowSpan();
            }
            nextColumn = endColumn;
        }
        for (int remainingRowSpan : remainingRowSpans) {
            if (remainingRowSpan < 1) {
                throw invalidGrid(rowIndex);
            }
        }
    }

    /**
     * 创建不包含业务内容的稳定网格异常。
     *
     * @param rowIndex 非法行下标
     * @return 表格网格异常
     */
    private PrintValidationException invalidGrid(int rowIndex) {
        return PrintValidationException.invalidDocument(
                "表格第 " + (rowIndex + 1) + " 行无法填满固定网格或与跨行单元格重叠");
    }

    /** @return 表格逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 表头行数 */
    public int headerRowCount() {
        return headerRowCount;
    }

    /** @return 不可变表格行 */
    public List<TableRow> rows() {
        return rows;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TableNode that)) {
            return false;
        }
        return headerRowCount == that.headerRowCount && id.equals(that.id) && rows.equals(that.rows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, headerRowCount, rows);
    }

    @Override
    public String toString() {
        return "TableNode[id=" + id
                + ", headerRowCount=" + headerRowCount + ", rows=" + rows + "]";
    }
}
