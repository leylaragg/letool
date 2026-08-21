package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;

import java.util.Objects;

/**
 * 根据物理测量结果按固定顺序放宽表格跨页约束。
 *
 * @author leyland
 */
final class PdfTableLayoutPlanner {

    /**
     * 解析当前轮次真正可执行的跨页策略。
     *
     * @param requested 模型请求的策略
     * @param tableFitsPage 整张表格是否能放入可用页面高度
     * @param everyRowGroupFitsPage 每个跨行组合是否都能放入一页
     * @return 不比请求策略更严格的可执行结果
     */
    TablePageBreakPolicy resolve(
            TablePageBreakPolicy requested,
            boolean tableFitsPage,
            boolean everyRowGroupFitsPage) {
        Objects.requireNonNull(requested, "requested 不能为空");
        if (requested == TablePageBreakPolicy.KEEP_TABLE && !tableFitsPage) {
            return everyRowGroupFitsPage
                    ? TablePageBreakPolicy.KEEP_ROWS : TablePageBreakPolicy.AUTO;
        }
        if (requested == TablePageBreakPolicy.KEEP_ROWS && !everyRowGroupFitsPage) {
            return TablePageBreakPolicy.AUTO;
        }
        return requested;
    }
}
