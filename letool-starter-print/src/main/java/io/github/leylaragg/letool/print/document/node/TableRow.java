package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

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

    /** 本行最小高度，单位为点；0 表示由内容决定。 */
    private final double minimumHeightPoints;

    /** 是否优先与下一行保持同页。 */
    private final boolean keepWithNext;

    /**
     * 创建表格行。
     *
     * @param cells 非空单元格列表
     */
    public TableRow(List<TableCell> cells) {
        this(cells, 0, false);
    }

    /**
     * 创建带最小高度和相邻行分页约束的表格行。
     *
     * @param cells 非空单元格列表，构建时复制为不可变列表
     * @param minimumHeightPoints 最小行高，单位为点，范围为 0 到 2000
     * @param keepWithNext 是否优先与下一行保持同页
     * @throws NullPointerException 单元格列表或元素为 null
     * @throws IllegalArgumentException 行高非有限或超出范围
     * @throws PrintValidationException 单元格列表为空
     */
    public TableRow(List<TableCell> cells, double minimumHeightPoints, boolean keepWithNext) {
        if (!Double.isFinite(minimumHeightPoints) || minimumHeightPoints < 0 || minimumHeightPoints > 2000) {
            throw new IllegalArgumentException("表格行最小高度无效");
        }
        this.minimumHeightPoints = minimumHeightPoints;
        this.keepWithNext = keepWithNext;
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

    /** @return 最小行高，单位为点；0 表示由内容决定 */
    public double minimumHeightPoints() {
        return minimumHeightPoints;
    }

    /** @return 是否优先与下一行保持同页 */
    public boolean keepWithNext() {
        return keepWithNext;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof TableRow that
                && cells.equals(that.cells)
                && Double.compare(minimumHeightPoints, that.minimumHeightPoints) == 0
                && keepWithNext == that.keepWithNext;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells, minimumHeightPoints, keepWithNext);
    }

    @Override
    public String toString() {
        return "TableRow[cells=" + cells + "]";
    }
}
