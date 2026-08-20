package io.github.leylaragg.letool.print.document.style;

/**
 * 表格跨页时希望优先保持的结构。
 *
 * @author leyland
 */
public enum TablePageBreakPolicy {
    /** 允许输出实现自然分页。 */
    AUTO,
    /** 优先保持单行或跨行组合完整。 */
    KEEP_ROWS,
    /** 优先保持整张表格位于同一页。 */
    KEEP_TABLE
}
