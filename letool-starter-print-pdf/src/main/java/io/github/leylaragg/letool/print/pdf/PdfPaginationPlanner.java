package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 根据每轮物理页数计算逻辑页码，并在限定轮次内判断收敛。
 *
 * @author leyland
 */
final class PdfPaginationPlanner {

    /** 所有 PDF 渲染调用共享的默认最大分页轮次。 */
    private static final int DEFAULT_MAX_PASSES = 5;

    /** 当前请求允许执行的最大分页轮次。 */
    private final int maxPasses;

    /** 上一轮不可变分页输入。 */
    private PdfPaginationPlan previous;

    /**
     * 创建使用统一默认分页轮次的规划器。
     *
     * @return 默认允许五轮分页的规划器
     */
    static PdfPaginationPlanner defaults() {
        return new PdfPaginationPlanner(DEFAULT_MAX_PASSES);
    }

    /**
     * 创建当前渲染请求独享的分页规划器。
     *
     * @param maxPasses 允许的最大轮次
     */
    PdfPaginationPlanner(int maxPasses) {
        if (maxPasses < 1 || maxPasses > 20) {
            throw new IllegalArgumentException("分页轮次必须在 1 到 20 之间");
        }
        this.maxPasses = maxPasses;
    }

    /**
     * 用保守的一页假设建立首次排版输入。
     *
     * @param document 完整 PDF 文档计划
     * @return 不包含目标页码的首次分页输入
     */
    PdfPaginationPlan initial(PdfDocumentPlan document) {
        Objects.requireNonNull(document, "document 不能为空");
        List<Integer> assumedPages = document.sequences().stream().map(sequence -> 1).toList();
        return calculate(document, assumedPages, Map.of(), 1, false);
    }

    /**
     * 把本轮真实测量转换成下一轮输入，相同输入可直接进入组装。
     *
     * @param document 完整 PDF 文档计划
     * @param rendered 本轮排版使用的输入
     * @param physicalPages 本轮测得的序列物理页数
     * @param targetPhysicalPages 本轮测得的全局目标页码
     * @return 已收敛计划或下一轮分页输入
     */
    PdfPaginationPlan advance(
            PdfDocumentPlan document,
            PdfPaginationPlan rendered,
            List<Integer> physicalPages,
            Map<String, Integer> targetPhysicalPages) {
        Objects.requireNonNull(rendered, "rendered 不能为空");
        PdfPaginationPlan measured = calculate(document, physicalPages,
                targetPhysicalPages, rendered.pass(), false);
        if (measured.sameLayoutInputs(rendered)) {
            return calculate(document, physicalPages,
                    targetPhysicalPages, rendered.pass(), true);
        }
        if (rendered.pass() >= maxPasses) {
            throw nonConvergent();
        }
        return calculate(document, physicalPages,
                targetPhysicalPages, rendered.pass() + 1, false);
    }

    /** 使用空目标页映射计算下一轮分页输入。 */
    PdfPaginationPlan next(PdfDocumentPlan document, List<Integer> physicalPages) {
        return next(document, physicalPages, Map.of());
    }

    /**
     * 根据最新测量结果计算下一轮分页输入。
     *
     * @param document 完整 PDF 文档计划
     * @param physicalPages 与页面序列一一对应的正物理页数
     * @param targetPhysicalPages 全局目标的一基物理页码
     * @return 当前轮次的不可变分页输入
     */
    PdfPaginationPlan next(
            PdfDocumentPlan document,
            List<Integer> physicalPages,
            Map<String, Integer> targetPhysicalPages) {
        Objects.requireNonNull(document, "document 不能为空");
        List<Integer> pages = List.copyOf(
                Objects.requireNonNull(physicalPages, "physicalPages 不能为空"));
        Map<String, Integer> targets = Map.copyOf(
                Objects.requireNonNull(targetPhysicalPages, "targetPhysicalPages 不能为空"));
        validateMeasurements(document, pages, targets);

        int pass = previous == null ? 1 : previous.pass() + 1;
        PdfPaginationPlan measured = calculate(document, pages, targets, pass, false);
        boolean stable = measured.sameLayoutInputs(previous);
        if (!stable && pass >= maxPasses) {
            throw nonConvergent();
        }
        PdfPaginationPlan current = calculate(document, pages, targets, pass, stable);
        previous = current;
        return current;
    }

    /** 把已校验的物理测量换算为每个序列的逻辑页码。 */
    private PdfPaginationPlan calculate(
            PdfDocumentPlan document,
            List<Integer> physicalPages,
            Map<String, Integer> targetPhysicalPages,
            int pass,
            boolean stable) {
        Objects.requireNonNull(document, "document 不能为空");
        List<Integer> pages = List.copyOf(
                Objects.requireNonNull(physicalPages, "physicalPages 不能为空"));
        Map<String, Integer> targets = Map.copyOf(
                Objects.requireNonNull(targetPhysicalPages, "targetPhysicalPages 不能为空"));
        validateMeasurements(document, pages, targets);
        List<PdfPaginationPlan.SequencePagination> sequences = new ArrayList<>(pages.size());
        int nextLogicalPage = 1;
        int logicalTotalPages = 0;
        for (int index = 0; index < pages.size(); index++) {
            PdfSequencePlan sequence = document.sequences().get(index);
            PageNumbering numbering = sequence.pageNumbering();
            int initialPageNumber = numbering.restartAt().orElse(nextLogicalPage);
            sequences.add(new PdfPaginationPlan.SequencePagination(
                    pages.get(index), initialPageNumber, numbering.includedInCount()));
            if (numbering.includedInCount()) {
                logicalTotalPages = Math.addExact(logicalTotalPages, pages.get(index));
                nextLogicalPage = Math.addExact(initialPageNumber, pages.get(index));
            }
        }

        return new PdfPaginationPlan(
                pass, sequences, logicalTotalPages, targets, stable);
    }

    /** 收敛失败统一保留内部原因，不向调用方暴露排版细节。 */
    private PrintRenderingException nonConvergent() {
        return PrintRenderingException.renderFailed(OutputFormat.PDF,
                new IllegalStateException("PDF 分页在限定轮次内未收敛"));
    }

    /** 测量向量必须完整且不包含零页或越界目标。 */
    private void validateMeasurements(
            PdfDocumentPlan document,
            List<Integer> physicalPages,
            Map<String, Integer> targetPhysicalPages) {
        if (physicalPages.size() != document.sequences().size()) {
            throw new IllegalArgumentException("物理页数必须与页面序列一一对应");
        }
        long total = 0;
        for (Integer pages : physicalPages) {
            if (pages == null || pages < 1) {
                throw new IllegalArgumentException("页面序列物理页数必须大于零");
            }
            total = Math.addExact(total, pages.longValue());
        }
        for (Map.Entry<String, Integer> target : targetPhysicalPages.entrySet()) {
            if (target.getKey() == null || target.getKey().isEmpty()
                    || target.getValue() == null || target.getValue() < 1
                    || target.getValue() > total) {
                throw new IllegalArgumentException("目录目标物理页码超出范围");
            }
        }
    }
}
