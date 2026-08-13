package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 具有可重复表头语义的表格节点。
 *
 * @author leyland
 */
public final class TableNode implements BlockNode {

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
        int columns = this.rows.get(0).effectiveColumns();
        if (this.rows.stream().anyMatch(row -> row.effectiveColumns() != columns)) {
            throw PrintValidationException.invalidDocument("表格各行有效列数必须一致");
        }
        this.headerRowCount = headerRowCount;
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
