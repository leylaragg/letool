package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 不可变表格行。
 *
 * @author leyland
 */
public final class TableRow {

    /** 不可变单元格列表。 */
    private final List<TableCell> cells;

    /**
     * 创建表格行。
     *
     * @param cells 非空单元格列表
     */
    public TableRow(List<TableCell> cells) {
        this.cells = List.copyOf(cells);
        if (this.cells.isEmpty()) {
            throw PrintValidationException.invalidDocument("表格行至少包含一个单元格");
        }
    }

    /** @return 不可变单元格列表 */
    public List<TableCell> cells() {
        return cells;
    }

    /** @return 本行所有单元格跨度之和 */
    long effectiveColumns() {
        return cells.stream().mapToLong(TableCell::colSpan).sum();
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof TableRow that && cells.equals(that.cells);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells);
    }

    @Override
    public String toString() {
        return "TableRow[cells=" + cells + "]";
    }
}
