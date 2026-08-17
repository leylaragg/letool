package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在物理合并完成后统一写入跨单元链接和文档大纲。
 *
 * @author leyland
 */
final class PdfNavigationWriter {

    /** 校验全部目标后再修改最终 PDF。 */
    Map<String, GlobalPosition> write(
            PDDocument pdf,
            DocumentModel document,
            List<PdfUnitResult> results,
            Map<String, String> sourceTargets) throws IOException {
        Map<String, GlobalPosition> targets = collectTargets(pdf, results);
        for (Map.Entry<String, String> link : sourceTargets.entrySet()) {
            if (!targets.containsKey(link.getValue())) {
                throw PrintValidationException.invalidDocument("PDF 内部链接目标不存在");
            }
        }
        List<BookmarkNode> bookmarks = bookmarks(document);
        for (BookmarkNode bookmark : bookmarks) {
            if (!targets.containsKey(bookmark.id())) {
                throw PrintValidationException.invalidDocument("PDF 书签没有可见目标");
            }
        }
        validateLinkSources(pdf, results);
        writeLinks(pdf, results, sourceTargets, targets);
        writeOutline(pdf, bookmarks, targets);
        return Map.copyOf(targets);
    }

    /** 把各单元的局部页码换算为合并后的全局页码。 */
    private Map<String, GlobalPosition> collectTargets(
            PDDocument pdf, List<PdfUnitResult> results) {
        Map<String, GlobalPosition> targets = new LinkedHashMap<>();
        int offset = 0;
        for (PdfUnitResult result : results) {
            for (Map.Entry<String, PdfLayoutSnapshot.Position> target
                    : result.snapshot().targets().entrySet()) {
                int pageIndex = offset + target.getValue().pageIndex();
                requirePage(pdf, pageIndex);
                targets.put(target.getKey(), new GlobalPosition(pageIndex, target.getValue().rectangle()));
            }
            offset += result.pageCount();
        }
        return targets;
    }

    /** 链接源页码也在写入任何注释前完成校验。 */
    private void validateLinkSources(PDDocument pdf, List<PdfUnitResult> results) {
        int offset = 0;
        for (PdfUnitResult result : results) {
            for (List<PdfLayoutSnapshot.Position> positions
                    : result.snapshot().linkSources().values()) {
                for (PdfLayoutSnapshot.Position position : positions) {
                    requirePage(pdf, offset + position.pageIndex());
                }
            }
            offset += result.pageCount();
        }
    }

    /** 按合并后的页码写入链接注释。 */
    private void writeLinks(
            PDDocument pdf,
            List<PdfUnitResult> results,
            Map<String, String> sourceTargets,
            Map<String, GlobalPosition> targets) throws IOException {
        int offset = 0;
        for (PdfUnitResult result : results) {
            for (Map.Entry<String, List<PdfLayoutSnapshot.Position>> source
                    : result.snapshot().linkSources().entrySet()) {
                String targetId = sourceTargets.get(source.getKey());
                if (targetId == null) {
                    continue;
                }
                PDPageXYZDestination destination = destination(pdf, targets.get(targetId));
                for (PdfLayoutSnapshot.Position local : source.getValue()) {
                    int pageIndex = offset + local.pageIndex();
                    PDAnnotationLink annotation = new PDAnnotationLink();
                    annotation.setRectangle(local.rectangle());
                    annotation.setDestination(destination);
                    pdf.getPage(pageIndex).getAnnotations().add(annotation);
                }
            }
            offset += result.pageCount();
        }
    }

    /** 提前收集书签，目标校验与写入阶段使用同一顺序。 */
    private List<BookmarkNode> bookmarks(DocumentModel document) {
        return DocumentTraversal.depthFirst(document).stream()
                .filter(BookmarkNode.class::isInstance).map(BookmarkNode.class::cast).toList();
    }

    /** 按文档顺序建立扁平大纲。 */
    private void writeOutline(
            PDDocument pdf,
            List<BookmarkNode> bookmarks,
            Map<String, GlobalPosition> targets) {
        if (bookmarks.isEmpty()) {
            return;
        }
        PDDocumentOutline outline = new PDDocumentOutline();
        for (BookmarkNode bookmark : bookmarks) {
            GlobalPosition target = targets.get(bookmark.id());
            PDOutlineItem item = new PDOutlineItem();
            item.setTitle(bookmark.label());
            item.setDestination(destination(pdf, target));
            outline.addLast(item);
        }
        outline.openNode();
        pdf.getDocumentCatalog().setDocumentOutline(outline);
    }

    /** 把全局位置转换为 PDFBox 可写入的页内目的地。 */
    private PDPageXYZDestination destination(PDDocument pdf, GlobalPosition position) {
        PDPageXYZDestination destination = new PDPageXYZDestination();
        destination.setPage(pdf.getPage(position.pageIndex()));
        destination.setLeft(Math.round(position.rectangle().getLowerLeftX()));
        destination.setTop(Math.round(position.rectangle().getUpperRightY()));
        destination.setZoom(0);
        return destination;
    }

    /** 合并结果中的所有坐标都必须落在实际页数范围内。 */
    private void requirePage(PDDocument pdf, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pdf.getNumberOfPages()) {
            throw PrintValidationException.invalidDocument("PDF 合并后的页面索引无效");
        }
    }

    /** 合并后页面索引与矩形。 */
    static final class GlobalPosition {
        private final int pageIndex;
        private final PDRectangle rectangle;

        /** 保存全局页码，并隔离调用方持有的矩形对象。 */
        GlobalPosition(int pageIndex, PDRectangle rectangle) {
            this.pageIndex = pageIndex;
            this.rectangle = new PDRectangle(
                    rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                    rectangle.getWidth(), rectangle.getHeight());
        }

        /** @return 合并文档内的零基页码 */
        int pageIndex() {
            return pageIndex;
        }

        /** @return 与内部状态隔离的页面矩形 */
        PDRectangle rectangle() {
            return new PDRectangle(
                    rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                    rectangle.getWidth(), rectangle.getHeight());
        }
    }
}
