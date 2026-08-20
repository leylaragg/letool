package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 把通用文档模型映射为仅含框架固定标签和样式的 XHTML。
 *
 * @author leyland
 */
final class PdfXhtmlRenderer {

    /** 按使用顺序固定的字体列表，最终回退字体始终排在末尾。 */
    private final List<PdfFont> fonts;

    /**
     * 创建单次渲染可复用的无状态映射器。
     *
     * @param fonts PDF 渲染器已经冻结的字体定义
     */
    PdfXhtmlRenderer(List<PdfFont> fonts) {
        Objects.requireNonNull(fonts, "fonts 不能为空");
        List<PdfFont> ordered = new ArrayList<>(fonts);
        if (ordered.stream().filter(PdfFont::fallback).count() > 1) {
            throw new IllegalArgumentException("最多只能配置一个最终回退字体");
        }
        ordered.sort(Comparator.comparing(PdfFont::fallback));
        this.fonts = List.copyOf(ordered);
    }

    /**
     * @param document 已完成数据绑定的通用文档模型
     * @return 可直接交给 PDF 排版器的受控 XHTML
     */
    String render(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        return render(PdfRenderView.complete(document), PdfRenderIds.create(document), false);
    }

    /**
     * 为分段管线写入稳定布局标记，并把导航交给最终 PDF 统一处理。
     *
     * @param document 本次排版的文档视图
     * @param ids 完整文档建立的稳定 ID
     * @param centralNavigation 是否关闭局部导航并保留源位置
     * @return 受控 XHTML
     */
    String render(PdfRenderView view, PdfRenderIds ids, boolean centralNavigation) {
        Objects.requireNonNull(view, "view 不能为空");
        Objects.requireNonNull(ids, "布局 ID 不能为空");
        StringBuilder output = new StringBuilder(4_096);
        writeDocument(output, view, ids, centralNavigation);
        return output.toString();
    }

    /** 写入 XHTML 外壳、固定样式和文档正文。 */
    private void writeDocument(
            StringBuilder output,
            PdfRenderView view,
            PdfRenderIds ids,
            boolean centralNavigation) {
        DocumentModel document = view.document();
        DocumentMetadata metadata = document.metadata();
        output.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"")
                .append(escapeAttribute(metadata.language() == null ? "und" : metadata.language()))
                .append("\"><head><meta charset=\"UTF-8\"/><title>");
        if (metadata.title() != null) {
            output.append(escapeText(metadata.title()));
        }
        output.append("</title>");
        if (!centralNavigation) {
            writeBookmarks(output, document);
        }
        output.append("<style>");
        writeStyle(output, view.pageLayout());
        output.append("</style></head><body>");
        for (BlockNode block : view.blocks()) {
            writeBlock(output, block, ids, centralNavigation);
        }
        output.append("</body></html>");
    }

    /** 页面、字体和分页规则都由框架生成，不接受模板侧 CSS。 */
    private void writeStyle(StringBuilder output, PageLayout layout) {
        int width = layout.orientation() == PageOrientation.PORTRAIT
                ? layout.pageSize().widthMicrometers()
                : layout.pageSize().heightMicrometers();
        int height = layout.orientation() == PageOrientation.PORTRAIT
                ? layout.pageSize().heightMicrometers()
                : layout.pageSize().widthMicrometers();
        Margins margins = layout.margins();
        output.append("@page { size: ").append(millimeters(width)).append("mm ")
                .append(millimeters(height)).append("mm; margin: ")
                .append(millimeters(margins.topMicrometers())).append("mm ")
                .append(millimeters(margins.rightMicrometers())).append("mm ")
                .append(millimeters(margins.bottomMicrometers())).append("mm ")
                .append(millimeters(margins.leftMicrometers())).append("mm; }")
                .append("body { margin: 0; font-family: ");
        writeFontFamilies(output);
        output.append("; }table { width: 100%; border-collapse: collapse; -fs-table-paginate: paginate; }")
                .append("thead { display: table-header-group; }")
                .append("tr { page-break-inside: avoid; }")
                .append(".page-break { page-break-after: always; }");
    }

    /** 把显式书签节点同步为 OpenHTMLToPDF 的受控大纲描述。 */
    private void writeBookmarks(StringBuilder output, DocumentModel document) {
        List<BookmarkNode> bookmarks = DocumentTraversal.depthFirst(document).stream()
                .filter(BookmarkNode.class::isInstance)
                .map(BookmarkNode.class::cast)
                .toList();
        if (bookmarks.isEmpty()) {
            return;
        }
        output.append("<bookmarks>");
        for (BookmarkNode bookmark : bookmarks) {
            output.append("<bookmark name=\"").append(escapeAttribute(bookmark.label()))
                    .append("\" href=\"#").append(escapeAttribute(bookmark.id()))
                    .append("\"></bookmark>");
        }
        output.append("</bookmarks>");
    }

    /** 按主字体、普通补充字体、最终回退字体的顺序写入 CSS。 */
    private void writeFontFamilies(StringBuilder output) {
        for (int index = 0; index < fonts.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append('\'').append(fonts.get(index).familyName()).append('\'');
        }
        if (!fonts.isEmpty()) {
            output.append(", ");
        }
        output.append("sans-serif");
    }

    /** 根据节点的跨格式语义写入固定块级标签。 */
    private void writeBlock(
            StringBuilder output,
            BlockNode block,
            PdfRenderIds ids,
            boolean centralNavigation) {
        if (block instanceof SectionNode section) {
            output.append("<section");
            writeId(output, section.id());
            output.append('>');
            section.children().forEach(child -> writeBlock(output, child, ids, centralNavigation));
            output.append("</section>");
        } else if (block instanceof HeadingNode heading) {
            String tag = "h" + heading.level();
            output.append('<').append(tag);
            writeId(output, ids.targetId(heading));
            output.append('>');
            heading.children().forEach(child -> writeInline(output, child, ids, centralNavigation));
            output.append("</").append(tag).append('>');
        } else if (block instanceof ParagraphNode paragraph) {
            output.append("<p");
            writeId(output, paragraph.id());
            output.append('>');
            paragraph.children().forEach(child -> writeInline(output, child, ids, centralNavigation));
            output.append("</p>");
        } else if (block instanceof TableNode table) {
            writeTable(output, table, ids, centralNavigation);
        } else if (block == PageBreakNode.INSTANCE) {
            output.append("<div class=\"page-break\"></div>");
        } else if (block instanceof AnnotationNode) {
            // 批注正文只进入 PDF 对象，不在页面正文中重复显示。
            return;
        } else if (block instanceof TableOfContentsNode) {
            throw PrintValidationException.invalidDocument("目录节点必须由 PDF 目录渲染器处理");
        } else {
            throw unsupported(block);
        }
    }

    /** 表头与表体分区保持模型语义，表头可由排版器跨页重复。 */
    private void writeTable(
            StringBuilder output,
            TableNode table,
            PdfRenderIds ids,
            boolean centralNavigation) {
        output.append("<table");
        writeId(output, table.id());
        output.append('>');
        if (table.headerRowCount() > 0) {
            output.append("<thead>");
            writeRows(output, table.rows().subList(0, table.headerRowCount()), true,
                    ids, centralNavigation);
            output.append("</thead>");
        }
        if (table.headerRowCount() < table.rows().size()) {
            output.append("<tbody>");
            writeRows(output, table.rows().subList(table.headerRowCount(), table.rows().size()), false,
                    ids, centralNavigation);
            output.append("</tbody>");
        }
        output.append("</table>");
    }

    /** 写入一个连续表格分区中的行。 */
    private void writeRows(
            StringBuilder output,
            List<TableRow> rows,
            boolean header,
            PdfRenderIds ids,
            boolean centralNavigation) {
        for (TableRow row : rows) {
            output.append("<tr>");
            for (TableCell cell : row.cells()) {
                writeCell(output, cell, header, ids, centralNavigation);
            }
            output.append("</tr>");
        }
    }

    /** 写入单元格跨度及其块级内容。 */
    private void writeCell(
            StringBuilder output,
            TableCell cell,
            boolean header,
            PdfRenderIds ids,
            boolean centralNavigation) {
        String tag = header ? "th" : "td";
        output.append('<').append(tag);
        if (cell.rowSpan() > 1) {
            output.append(" rowspan=\"").append(cell.rowSpan()).append('\"');
        }
        if (cell.colSpan() > 1) {
            output.append(" colspan=\"").append(cell.colSpan()).append('\"');
        }
        output.append('>');
        cell.content().forEach(block -> writeBlock(output, block, ids, centralNavigation));
        output.append("</").append(tag).append('>');
    }

    /** 根据节点语义写入转义文本、书签目标或内部链接。 */
    private void writeInline(
            StringBuilder output,
            InlineNode inline,
            PdfRenderIds ids,
            boolean centralNavigation) {
        if (inline instanceof TextNode text) {
            output.append(escapeText(text.text()));
        } else if (inline instanceof BookmarkNode bookmark) {
            output.append("<span id=\"").append(escapeAttribute(bookmark.id()))
                    .append("\" class=\"bookmark\">").append(escapeText(bookmark.label()))
                    .append("</span>");
        } else if (inline instanceof InternalLinkNode link) {
            if (centralNavigation) {
                output.append("<span id=\"").append(escapeAttribute(ids.sourceId(link)))
                        .append("\" class=\"internal-link\">");
                link.label().forEach(label -> writeInline(output, label, ids, true));
                output.append("</span>");
            } else {
                output.append("<a href=\"#").append(escapeAttribute(link.targetId())).append("\">");
                link.label().forEach(label -> writeInline(output, label, ids, false));
                output.append("</a>");
            }
        } else {
            throw unsupported(inline);
        }
    }

    /** 非空逻辑 ID 才会进入输出。 */
    private void writeId(StringBuilder output, String id) {
        if (!id.isEmpty()) {
            output.append(" id=\"").append(escapeAttribute(id)).append('\"');
        }
    }

    /** 将已经校验的整数微米转为不依赖区域设置的毫米数值。 */
    private String millimeters(int micrometers) {
        return BigDecimal.valueOf(micrometers, 3).stripTrailingZeros().toPlainString();
    }

    /** 转义普通文本中的 XML 保留字符。 */
    private String escapeText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** 属性与文本使用同一组 XML 转义规则。 */
    private String escapeAttribute(String value) {
        return escapeText(value);
    }

    /** 能力检查遗漏节点时仍以安全的模型异常终止。 */
    private PrintValidationException unsupported(Object node) {
        return PrintValidationException.invalidDocument(
                "PDF XHTML 映射不支持节点类型：" + node.getClass().getSimpleName());
    }
}
