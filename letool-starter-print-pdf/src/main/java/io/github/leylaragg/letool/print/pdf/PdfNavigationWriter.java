package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentNavigationOptions;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PageMode;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSFloat;
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
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;

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
        List<BookmarkNode> bookmarks = bookmarks(document);
        PdfRenderIds ids = PdfRenderIds.create(document);
        for (BookmarkNode bookmark : bookmarks) {
            GlobalPosition actual = targets.get(ids.targetId(bookmark.effectiveTargetId()));
            if (actual == null) {
                throw PrintValidationException.invalidDocument("PDF 书签没有可见目标");
            }
            // 批注和链接共用逻辑目标位置，元数据书签无需生成布局盒。
            targets.put(bookmark.id(), actual);
        }
        for (Map.Entry<String, String> link : sourceTargets.entrySet()) {
            if (!targets.containsKey(link.getValue())) {
                throw PrintValidationException.invalidDocument("PDF 内部链接目标不存在");
            }
        }
        DocumentNavigationOptions navigation = document.options().navigation();
        String initialTarget = ids.targetId(navigation.initialTarget());
        if (!initialTarget.isEmpty() && !targets.containsKey(initialTarget)) {
            throw PrintValidationException.invalidDocument("PDF 初始目标没有可见位置");
        }
        validateLinkSources(pdf, results);
        writeLinks(pdf, results, sourceTargets, targets, navigation);
        writeOutline(pdf, bookmarks, targets, navigation, ids);
        if (!initialTarget.isEmpty()) {
            pdf.getDocumentCatalog().setOpenAction(destination(pdf, targets.get(initialTarget), navigation));
        }
        if (navigation.showBookmarks()) {
            pdf.getDocumentCatalog().setPageMode(PageMode.USE_OUTLINES);
        }
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
        Map<String, GlobalPosition> targets,
        DocumentNavigationOptions navigation) throws IOException {
        int offset = 0;
        for (PdfUnitResult result : results) {
            for (Map.Entry<String, List<PdfLayoutSnapshot.Position>> source
                    : result.snapshot().linkSources().entrySet()) {
                String targetId = sourceTargets.get(source.getKey());
                if (targetId == null) {
                    continue;
                }
                PDPageXYZDestination destination = destination(pdf, targets.get(targetId), navigation);
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

    /** 先创建全部节点，再按原同级顺序连接，最后设置组的折叠状态。 */
    private void writeOutline(
            PDDocument pdf,
            List<BookmarkNode> bookmarks,
        Map<String, GlobalPosition> targets,
        DocumentNavigationOptions navigation,
        PdfRenderIds ids) {
        if (bookmarks.isEmpty()) {
            return;
        }
        PDDocumentOutline outline = new PDDocumentOutline();
        Map<String, PDOutlineItem> items = new LinkedHashMap<>();
        Map<String, List<BookmarkNode>> children = new LinkedHashMap<>();
        for (BookmarkNode bookmark : bookmarks) {
            GlobalPosition target = targets.get(ids.targetId(bookmark.effectiveTargetId()));
            PDOutlineItem item = new PDOutlineItem();
            item.setTitle(bookmark.label());
            item.setDestination(destination(pdf, target, navigation));
            items.put(bookmark.id(), item);
            children.computeIfAbsent(bookmark.parentId(), key -> new ArrayList<>()).add(bookmark);
        }
        for (BookmarkNode bookmark : bookmarks) {
            PDOutlineItem item = items.get(bookmark.id());
            if (bookmark.parentId().isEmpty()) {
                outline.addLast(item);
            } else {
                items.get(bookmark.parentId()).addLast(item);
            }
        }
        outline.openNode();
        items.values().forEach(PDOutlineItem::openNode);
        List<BookmarkNode> treeOrder = new ArrayList<>();
        Deque<BookmarkNode> pending = new ArrayDeque<>(children.getOrDefault("", List.of()));
        while (!pending.isEmpty()) {
            BookmarkNode bookmark = pending.removeFirst();
            treeOrder.add(bookmark);
            pending.addAll(children.getOrDefault(bookmark.id(), List.of()));
        }
        for (int index = treeOrder.size() - 1; index >= 0; index--) {
            BookmarkNode bookmark = treeOrder.get(index);
            if (bookmark.collapsed()) {
                items.get(bookmark.id()).closeNode();
            }
        }
        pdf.getDocumentCatalog().setDocumentOutline(outline);
    }

    /** 把全局位置转换为 PDFBox 可写入的页内目的地。 */
    private PDPageXYZDestination destination(PDDocument pdf, GlobalPosition position,
        DocumentNavigationOptions navigation) {
        PDPageXYZDestination destination = new PDPageXYZDestination();
        destination.setPage(pdf.getPage(position.pageIndex()));
        if (navigation.destinationMode() == DocumentNavigationOptions.DestinationMode.PAGE_TOP) {
            destination.setLeft(0);
            destination.getCOSObject().set(3, new
                COSFloat(pdf.getPage(position.pageIndex()).getMediaBox().getHeight()));
            destination.getCOSObject().set(4, COSNull.NULL);
        } else {
            destination.setLeft(Math.round(position.rectangle().getLowerLeftX()));
            destination.setTop(Math.round(position.rectangle().getUpperRightY()));
            destination.setZoom(0);
        }
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
