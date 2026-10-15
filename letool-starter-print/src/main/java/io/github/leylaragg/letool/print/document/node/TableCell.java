package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 表格单元格内容及跨行跨列信息。
 *
 * @author leyland
 */
public final class TableCell {

    /** 不可变块级内容。 */
    private final List<BlockNode> content;

    /** 可选单元格样式名。 */
    private final String styleName;

    /** 跨行数。 */
    private final int rowSpan;

    /** 跨列数。 */
    private final int colSpan;
    /** 表格行拆页时是否在后续片段重复本单元格内容。 */
    private final boolean repeatOnSplit;

    /**
     * 创建使用框架默认单元格样式的单元格。
     *
     * @param content 块级内容
     * @param rowSpan 跨行数
     * @param colSpan 跨列数
     */
    public TableCell(List<BlockNode> content, int rowSpan, int colSpan) {
        this("", content, rowSpan, colSpan);
    }

    /**
     * 创建不可变单元格。
     *
     * @param styleName 单元格样式名；空字符串表示使用框架默认样式
     * @param content 块级内容
     * @param rowSpan 跨行数
     * @param colSpan 跨列数
     */
    public TableCell(String styleName, List<BlockNode> content, int rowSpan, int colSpan) {
        this(styleName, content, rowSpan, colSpan, false);
    }

    /**
     * 创建带跨页重复策略的不可变单元格。
     *
     * @param styleName 样式名；null 或空字符串使用默认样式
     * @param content 块级内容，构建时复制为不可变列表
     * @param rowSpan 跨行数，范围为 1 到 1000
     * @param colSpan 跨列数，范围为 1 到 1000
     * @param repeatOnSplit 拆页后是否重复本单元格内容
     * @throws NullPointerException 内容列表或元素为 null
     * @throws PrintValidationException 跨行数或跨列数超出范围
     */
    public TableCell(String styleName, List<BlockNode> content, int rowSpan, int colSpan, boolean repeatOnSplit) {
        this.repeatOnSplit = repeatOnSplit;
        this.styleName = styleName == null ? "" : styleName;
        this.content = List.copyOf(content);
        if (rowSpan < 1 || rowSpan > 1_000 || colSpan < 1 || colSpan > 1_000) {
            throw PrintValidationException.invalidDocument("表格跨度必须在 1 到 1000 之间");
        }
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
    }

    /** @return 不可变块级内容 */
    public List<BlockNode> content() {
        return content;
    }

    /** @return 单元格样式名 */
    public String styleName() {
        return styleName;
    }

    /** @return 跨行数 */
    public int rowSpan() {
        return rowSpan;
    }

    /** @return 跨列数 */
    public int colSpan() {
        return colSpan;
    }

    /** @return 拆页后是否重复本单元格内容 */
    public boolean repeatOnSplit() {
        return repeatOnSplit;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TableCell that)) {
            return false;
        }
        return repeatOnSplit == that.repeatOnSplit && rowSpan == that.rowSpan && colSpan == that.colSpan
                && styleName.equals(that.styleName) && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styleName, content, rowSpan, colSpan, repeatOnSplit);
    }

    @Override
    public String toString() {
        return "TableCell[styleName=" + styleName + ", content=" + content
                + ", rowSpan=" + rowSpan + ", colSpan=" + colSpan + "]";
    }
}
