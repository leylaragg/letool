package io.github.leylaragg.letool.print.pdf.internal.layout;

import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 表格跨页策略规划器的既有降级行为回归。 */
class PdfTableLayoutPlannerTest {

    /** 跨页策略只按 KEEP_TABLE、KEEP_ROWS、AUTO 的顺序降级。 */
    @Test
    void shouldDowngradePageBreakPolicyInOrder() {
        PdfTableLayoutPlanner planner = new PdfTableLayoutPlanner();

        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, true, true))
                .isEqualTo(TablePageBreakPolicy.KEEP_TABLE);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, false, true))
                .isEqualTo(TablePageBreakPolicy.KEEP_ROWS);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_ROWS, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
        assertThat(planner.resolve(TablePageBreakPolicy.AUTO, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
    }

}
