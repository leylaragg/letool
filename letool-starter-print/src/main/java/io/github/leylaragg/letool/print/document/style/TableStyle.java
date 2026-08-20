package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 表格宽度、列布局和跨页偏好的不可变样式。
 *
 * @author leyland
 */
public final class TableStyle {

    /** 比较百分比合计时允许的浮点误差。 */
    private static final double PERCENT_TOLERANCE = 0.000_001;

    /** 框架默认表格样式。 */
    private static final TableStyle DEFAULT = builder().build();

    /** 表格总宽度。 */
    private final DocumentLength width;

    /** 列布局方式。 */
    private final TableLayoutMode layoutMode;

    /** 不可修改的列宽。 */
    private final List<DocumentLength> columnWidths;

    /** 是否跨页重复表头。 */
    private final boolean repeatHeader;

    /** 跨页策略。 */
    private final TablePageBreakPolicy pageBreakPolicy;

    /** 从 Builder 创建表格样式。 */
    private TableStyle(Builder builder) {
        this.width = Objects.requireNonNull(builder.width, "width 不能为空");
        width.requireUnit("表格宽度", DocumentLength.Unit.MILLIMETER, DocumentLength.Unit.PERCENT);
        width.requirePositive("表格宽度");
        this.layoutMode = Objects.requireNonNull(builder.layoutMode, "layoutMode 不能为空");
        this.columnWidths = List.copyOf(builder.columnWidths);
        this.repeatHeader = builder.repeatHeader;
        this.pageBreakPolicy = Objects.requireNonNull(builder.pageBreakPolicy, "pageBreakPolicy 不能为空");
        validateColumns();
    }

    /** @return 新的表格样式 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 框架默认表格样式 */
    public static TableStyle defaults() {
        return DEFAULT;
    }

    /** @return 表格总宽度 */
    public DocumentLength width() {
        return width;
    }

    /** @return 列布局方式 */
    public TableLayoutMode layoutMode() {
        return layoutMode;
    }

    /** @return 不可修改的列宽 */
    public List<DocumentLength> columnWidths() {
        return columnWidths;
    }

    /** @return 是否跨页重复表头 */
    public boolean repeatHeader() {
        return repeatHeader;
    }

    /** @return 跨页策略 */
    public TablePageBreakPolicy pageBreakPolicy() {
        return pageBreakPolicy;
    }

    /** 同一组表格属性代表同一个样式值。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TableStyle that)) {
            return false;
        }
        return repeatHeader == that.repeatHeader
                && width.equals(that.width)
                && layoutMode == that.layoutMode
                && columnWidths.equals(that.columnWidths)
                && pageBreakPolicy == that.pageBreakPolicy;
    }

    /** @return 与表格样式值一致的哈希码 */
    @Override
    public int hashCode() {
        return Objects.hash(width, layoutMode, columnWidths, repeatHeader, pageBreakPolicy);
    }

    /** 校验固定布局、列宽单位和百分比合计。 */
    private void validateColumns() {
        if (layoutMode == TableLayoutMode.FIXED && columnWidths.isEmpty()) {
            throw PrintValidationException.invalidDocument("固定表格布局必须声明列宽");
        }
        DocumentLength.Unit unit = null;
        double total = 0;
        for (DocumentLength columnWidth : columnWidths) {
            Objects.requireNonNull(columnWidth, "columnWidths 不允许 null");
            columnWidth.requireUnit("表格列宽", DocumentLength.Unit.MILLIMETER, DocumentLength.Unit.PERCENT);
            columnWidth.requirePositive("表格列宽");
            if (unit != null && unit != columnWidth.unit()) {
                throw PrintValidationException.invalidDocument("同一表格的列宽必须使用相同单位");
            }
            unit = columnWidth.unit();
            total += columnWidth.value();
        }
        if (unit == DocumentLength.Unit.PERCENT && Math.abs(total - 100) > PERCENT_TOLERANCE) {
            throw PrintValidationException.invalidDocument("百分比列宽合计必须为 100");
        }
    }

    /**
     * 表格样式构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 默认占满所属区域。 */
        private DocumentLength width = DocumentLength.percent(100);

        /** 默认自动布局。 */
        private TableLayoutMode layoutMode = TableLayoutMode.AUTO;

        /** 默认不声明列宽。 */
        private List<DocumentLength> columnWidths = List.of();

        /** 默认不重复表头。 */
        private boolean repeatHeader;

        /** 默认自然分页。 */
        private TablePageBreakPolicy pageBreakPolicy = TablePageBreakPolicy.AUTO;

        /**
         * 设置表格总宽度。
         *
         * @param width 表格总宽度
         * @return 当前 Builder
         */
        public Builder width(DocumentLength width) {
            this.width = width;
            return this;
        }

        /**
         * 设置列布局方式。
         *
         * @param layoutMode 列布局方式
         * @return 当前 Builder
         */
        public Builder layoutMode(TableLayoutMode layoutMode) {
            this.layoutMode = layoutMode;
            return this;
        }

        /**
         * 设置列宽列表。
         *
         * @param columnWidths 列宽列表
         * @return 当前 Builder
         */
        public Builder columnWidths(List<DocumentLength> columnWidths) {
            this.columnWidths = Objects.requireNonNull(columnWidths, "columnWidths 不能为空");
            return this;
        }

        /**
         * 设置是否跨页重复表头。
         *
         * @param repeatHeader 是否跨页重复表头
         * @return 当前 Builder
         */
        public Builder repeatHeader(boolean repeatHeader) {
            this.repeatHeader = repeatHeader;
            return this;
        }

        /**
         * 设置表格跨页策略。
         *
         * @param pageBreakPolicy 跨页策略
         * @return 当前 Builder
         */
        public Builder pageBreakPolicy(TablePageBreakPolicy pageBreakPolicy) {
            this.pageBreakPolicy = pageBreakPolicy;
            return this;
        }

        /** @return 不可变表格样式 */
        public TableStyle build() {
            return new TableStyle(this);
        }
    }
}
