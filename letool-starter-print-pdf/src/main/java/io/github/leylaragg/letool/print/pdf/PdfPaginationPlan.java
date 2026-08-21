package io.github.leylaragg.letool.print.pdf;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一轮 PDF 排版使用的物理页数和逻辑页码快照。
 *
 * @author leyland
 */
final class PdfPaginationPlan {

    /** 当前分页轮次，从一开始。 */
    private final int pass;

    /** 每个页面序列的页码输入。 */
    private final List<SequencePagination> sequences;

    /** 所有计入序列的物理页数总和。 */
    private final int logicalTotalPages;

    /** 目录等全局导航目标的物理页码。 */
    private final Map<String, Integer> targetPhysicalPages;

    /** 当前输入是否与上一轮完全一致。 */
    private final boolean stable;

    /** 保存一轮已经完成校验的分页输入。 */
    PdfPaginationPlan(
            int pass,
            List<SequencePagination> sequences,
            int logicalTotalPages,
            Map<String, Integer> targetPhysicalPages,
            boolean stable) {
        this.pass = pass;
        this.sequences = List.copyOf(sequences);
        this.logicalTotalPages = logicalTotalPages;
        this.targetPhysicalPages = Map.copyOf(targetPhysicalPages);
        this.stable = stable;
    }

    /** @return 当前分页轮次 */
    int pass() {
        return pass;
    }

    /** @return 指定页面序列的分页输入 */
    SequencePagination sequence(int sourceIndex) {
        if (sourceIndex < 0 || sourceIndex >= sequences.size()) {
            throw new IllegalArgumentException("页面序列索引超出范围");
        }
        return sequences.get(sourceIndex);
    }

    /** @return 全部页面序列的分页输入 */
    List<SequencePagination> sequences() {
        return sequences;
    }

    /** @return 所有计入序列的逻辑总页数 */
    int logicalTotalPages() {
        return logicalTotalPages;
    }

    /** @return 全局导航目标的一基物理页码 */
    Map<String, Integer> targetPhysicalPages() {
        return targetPhysicalPages;
    }

    /** @return 当前分页输入是否已经收敛 */
    boolean stable() {
        return stable;
    }

    /** 物理页数、逻辑起始值和目录目标全部一致才算同一轮输入。 */
    boolean sameLayoutInputs(PdfPaginationPlan other) {
        return other != null
                && sequences.equals(other.sequences)
                && logicalTotalPages == other.logicalTotalPages
                && targetPhysicalPages.equals(other.targetPhysicalPages);
    }

    /** 单个页面序列进入排版器的页码参数。 */
    static final class SequencePagination {

        /** 当前序列实际物理页数。 */
        private final int physicalPageCount;

        /** OpenHTMLToPDF 使用的序列首页页码。 */
        private final int initialPageNumber;

        /** 当前序列是否显示逻辑页码。 */
        private final boolean showsLogicalPageNumber;

        /** 保存一个页面序列的分页参数。 */
        SequencePagination(
                int physicalPageCount,
                int initialPageNumber,
                boolean showsLogicalPageNumber) {
            this.physicalPageCount = physicalPageCount;
            this.initialPageNumber = initialPageNumber;
            this.showsLogicalPageNumber = showsLogicalPageNumber;
        }

        /** @return 当前序列的物理页数 */
        int physicalPageCount() {
            return physicalPageCount;
        }

        /** @return 当前序列首页显示的逻辑页码 */
        int initialPageNumber() {
            return initialPageNumber;
        }

        /** @return 当前序列是否显示逻辑页码 */
        boolean showsLogicalPageNumber() {
            return showsLogicalPageNumber;
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof SequencePagination other)) {
                return false;
            }
            return physicalPageCount == other.physicalPageCount
                    && initialPageNumber == other.initialPageNumber
                    && showsLogicalPageNumber == other.showsLogicalPageNumber;
        }

        @Override
        public int hashCode() {
            return Objects.hash(physicalPageCount, initialPageNumber, showsLogicalPageNumber);
        }
    }
}
