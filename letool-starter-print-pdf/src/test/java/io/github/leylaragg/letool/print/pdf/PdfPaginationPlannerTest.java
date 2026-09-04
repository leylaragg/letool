package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 物理分页到逻辑页码的规划和收敛测试。
 *
 * @author leyland
 */
class PdfPaginationPlannerTest {

    /** 只有计入规则参与总页数，重启值只改变显示页码。 */
    @Test
    void shouldCountOnlyIncludedPhysicalPages() {
        PdfPaginationPlanner planner = new PdfPaginationPlanner(5);
        PdfDocumentPlan document = documentPlan(
                PageNumbering.counted(),
                PageNumbering.excluded(),
                PageNumbering.countedFrom(10));

        PdfPaginationPlan plan = planner.next(document, List.of(2, 1, 3));

        assertThat(plan.logicalTotalPages()).isEqualTo(5);
        assertThat(plan.sequence(0).initialPageNumber()).isEqualTo(1);
        assertThat(plan.sequence(0).showsLogicalPageNumber()).isTrue();
        assertThat(plan.sequence(1).showsLogicalPageNumber()).isFalse();
        assertThat(plan.sequence(2).initialPageNumber()).isEqualTo(10);
    }

    /** 延续序列从前一个计入序列的末页继续编号。 */
    @Test
    void shouldContinueLogicalPageNumberAcrossCountedSequences() {
        PdfPaginationPlan plan = new PdfPaginationPlanner(5).next(documentPlan(
                PageNumbering.countedFrom(5),
                PageNumbering.excluded(),
                PageNumbering.counted()), List.of(2, 4, 3));

        assertThat(plan.sequence(0).initialPageNumber()).isEqualTo(5);
        assertThat(plan.sequence(2).initialPageNumber()).isEqualTo(7);
        assertThat(plan.logicalTotalPages()).isEqualTo(5);
    }

    /** 物理页数和目录目标都一致时，下一轮计划才算稳定。 */
    @Test
    void shouldRequireSameLayoutInputsBeforeStable() {
        PdfPaginationPlanner planner = new PdfPaginationPlanner(5);
        PdfDocumentPlan document = documentPlan(PageNumbering.counted());

        PdfPaginationPlan first = planner.next(document, List.of(2), Map.of("chapter", 2));
        PdfPaginationPlan changed = planner.next(document, List.of(2), Map.of("chapter", 1));
        PdfPaginationPlan stable = planner.next(document, List.of(2), Map.of("chapter", 1));

        assertThat(first.stable()).isFalse();
        assertThat(changed.stable()).isFalse();
        assertThat(stable.stable()).isTrue();
        assertThat(stable.targetPhysicalPages()).containsEntry("chapter", 1);
    }

    /** 第五轮输入仍在变化时，规划器以安全渲染异常停止。 */
    @Test
    void shouldStopAfterMaximumPasses() {
        PdfPaginationPlanner planner = new PdfPaginationPlanner(5);
        PdfDocumentPlan document = documentPlan(PageNumbering.counted());

        for (int pages = 1; pages <= 4; pages++) {
            assertThat(planner.next(document, List.of(pages)).stable()).isFalse();
        }
        assertThatThrownBy(() -> planner.next(document, List.of(5)))
                .isInstanceOf(PrintRenderingException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause().hasMessageContaining("未收敛");
    }

    /** 默认规划器沿用五轮分页上限。 */
    @Test
    void shouldUseFivePassesByDefault() {
        PdfPaginationPlanner planner = PdfPaginationPlanner.defaults();
        PdfDocumentPlan document = documentPlan(PageNumbering.counted());

        for (int pages = 1; pages <= 4; pages++) {
            assertThat(planner.next(document, List.of(pages)).stable()).isFalse();
        }
        assertThatThrownBy(() -> planner.next(document, List.of(5)))
                .isInstanceOf(PrintRenderingException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** 页数向量必须与页面序列一一对应且全部为正数。 */
    @Test
    void shouldRejectInvalidPhysicalPageCounts() {
        PdfPaginationPlanner planner = new PdfPaginationPlanner(5);
        PdfDocumentPlan document = documentPlan(PageNumbering.counted());

        assertThatThrownBy(() -> planner.next(document, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页面序列");
        assertThatThrownBy(() -> planner.next(document, List.of(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物理页数");
    }

    /** 用指定页码规则创建页面序列计划。 */
    private static PdfDocumentPlan documentPlan(PageNumbering... numberings) {
        List<PageSequence> sequences = java.util.Arrays.stream(numberings)
                .map(numbering -> new PageSequence(
                        PageLayout.a4Portrait(), PageRegion.empty(), PageRegion.empty(),
                        numbering, List.of(new ParagraphNode(
                                "", List.of(new TextNode("body"))))))
                .toList();
        return PdfDocumentPlan.create(new DocumentModel(
                DocumentMetadata.empty(), StyleSheet.empty(), sequences));
    }
}
