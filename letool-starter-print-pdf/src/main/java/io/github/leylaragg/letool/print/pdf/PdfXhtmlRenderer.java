package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把通用文档模型映射为仅含框架固定标签和样式的 XHTML。
 *
 * @author leyland
 */
final class PdfXhtmlRenderer {

    /** 布局快照用来定位页眉原始盒子的固定 ID。 */
    static final String HEADER_REGION_ID = "letool-page-header-region";

    /** 布局快照用来定位页脚原始盒子的固定 ID。 */
    static final String FOOTER_REGION_ID = "letool-page-footer-region";

    /** 按 CSS 回退顺序固定的字体族，最终回退族排在末尾。 */
    private final List<String> fontFamilies;

    /** 命名文本样式在写入 CSS 前需要核对字体面。 */
    private final PdfFontCatalog fontCatalog;

    /**
     * 创建单次渲染可复用的无状态映射器。
     *
     * @param fontCatalog PDF 渲染器已经冻结的字体目录
     */
    PdfXhtmlRenderer(PdfFontCatalog fontCatalog) {
        Objects.requireNonNull(fontCatalog, "fontCatalog 不能为空");
        this.fontCatalog = fontCatalog;
        Map<String, Boolean> families = new LinkedHashMap<>();
        for (PdfFont font : fontCatalog.fonts()) {
            if (!font.namedOnly()) {
                families.merge(font.familyName(), font.fallbackFamily(), Boolean::logicalOr);
            }
        }
        this.fontFamilies = families.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<String, Boolean>::getValue))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * @param document 已完成数据绑定的通用文档模型
     * @return 可直接交给 PDF 排版器的受控 XHTML
     */
    String render(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        if (document.pageSequences().size() != 1) {
            throw new IllegalArgumentException("直接 XHTML 映射只接受一个页面序列");
        }
        PdfDocumentPlan plan = PdfDocumentPlan.create(document);
        PdfSequencePlan sequence = plan.sequences().get(0);
        PdfPaginationPlan pagination = PdfPaginationPlanner.defaults().initial(plan);
        return render(plan, sequence, document.pageSequences().get(0).body(),
                pagination, plan.renderIds(), false);
    }

    /**
     * 为一个页面序列排版单元写出区域、正文和当前轮页码输入。
     *
     * @param document 完整 PDF 文档计划
     * @param sequence 当前页面序列
     * @param blocks 当前正文排版单元
     * @param pagination 本轮分页输入
     * @param ids 文档级稳定布局 ID
     * @param centralNavigation 是否把导航交给全局组装器
     * @return 当前序列可独立排版的受控 XHTML
     */
    String render(
            PdfDocumentPlan document,
            PdfSequencePlan sequence,
            List<BlockNode> blocks,
            PdfPaginationPlan pagination,
            PdfRenderIds ids,
            boolean centralNavigation) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(sequence, "sequence 不能为空");
        Objects.requireNonNull(blocks, "blocks 不能为空");
        Objects.requireNonNull(pagination, "pagination 不能为空");
        Objects.requireNonNull(ids, "ids 不能为空");
        PdfStyleCatalog styles = PdfStyleCatalog.compile(document.styleSheet(), fontCatalog,
                document.document().options(), usesDefaultFreeText(document.document()));
        PdfPaginationPlan.SequencePagination sequencePagination =
                pagination.sequence(sequence.sourceIndex());
        PageValues pageValues = new PageValues(
                sequencePagination.showsLogicalPageNumber(), pagination.logicalTotalPages());
        StringBuilder output = new StringBuilder(4_096);
        writeDocument(output, document.document(), sequence.pageLayout(),
                sequence.header(), sequence.footer(), blocks, ids, styles,
                pageValues, centralNavigation);
        return output.toString();
    }

    /** 文字框空样式遵循节点默认值，普通文本节点仍保留原有父级继承语义。 */
    private static boolean usesDefaultFreeText(DocumentModel document) {
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            if (node instanceof FreeTextNode freeText && freeText.textStyleName().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 写入一份页面布局明确的 XHTML 文档。 */
    private void writeDocument(
            StringBuilder output,
            DocumentModel document,
            PageLayout pageLayout,
            PageRegion header,
            PageRegion footer,
            List<BlockNode> blocks,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation) {
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
        writeStyle(output, pageLayout, !header.isEmpty(), !footer.isEmpty(), document.options());
        output.append(styles.css());
        for (BlockNode block : blocks) {
            if (block instanceof
                io.github.leylaragg.letool.print.document.node.ContentsTitleNode title) {
                output.append('#').append(title.id()).append("{margin-bottom:0;}");
            }
        }
        output.append("</style></head><body>");
        writeRegion(output, header, HEADER_REGION_ID, "lt-page-header",
                ids, styles, pageValues, centralNavigation);
        writeRegion(output, footer, FOOTER_REGION_ID, "lt-page-footer",
                ids, styles, pageValues, centralNavigation);
        output.append("<div id=\"letool-body-region\">");
        for (BlockNode block : blocks) {
            writeBlock(output, block, ids, styles, pageValues, centralNavigation, false);
        }
        output.append("</div></body></html>");
    }

    /** 页面、字体和分页规则都由框架生成，不接受模板侧 CSS。 */
    private void writeStyle(
            StringBuilder output,
            PageLayout layout,
            boolean hasHeader,
        boolean hasFooter, io.github.leylaragg.letool.print.document.DocumentOptions options) {
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
        writeFontFamilies(output, options);
        output.append("; }table { width: 100%; border-collapse: collapse; -fs-table-paginate: paginate; }")
                .append("thead { display: table-header-group; }")
                .append("tr { page-break-inside: avoid; }")
                .append(".page-break { page-break-after: always; }")
                .append(".lt-page-number:before{content:counter(page);}");
        // 重复区域的表格整体参与所在行布局；外壳去掉行内基线留白，正文表格仍按页分段。
        output.append(".lt-repeated-table{line-height:0;}")
                .append(".lt-repeated-table>table{display:inline-table;vertical-align:top;")
                .append("line-height:normal;-fs-table-paginate:auto;}");
        if (hasHeader) {
            output.append("@page{@top-center{content:element(lt-page-header);");
            if (layout.headerTopPoints() >= 0)
            {
                output.append("vertical-align:top;padding-top:").append(layout.headerTopPoints()).append("pt;");
            }
            output.append("}}.lt-page-header{position:running(lt-page-header);width:")
                .append(millimeters(width - margins.leftMicrometers() - margins.rightMicrometers())).append("mm;}");
        }
        if (hasFooter) {
            output.append("@page{@bottom-center{content:element(lt-page-footer);");
            if (layout.footerBaselinePoints() >= 0) {
                output.append("vertical-align:top;padding-top:0pt;");
            }
            output.append("}}.lt-page-footer{position:running(lt-page-footer);width:")
                .append(millimeters(width - margins.leftMicrometers() - margins.rightMicrometers())).append("mm;}");
        }
    }

    /** 非空页面区域以 running element 写入一次，由排版器逐页重复。 */
    private void writeRegion(
            StringBuilder output,
            PageRegion region,
            String id,
            String className,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation) {
        if (region.isEmpty()) {
            return;
        }
        output.append("<div id=\"").append(id).append("\" class=\"")
                .append(className).append("\">");
        for (BlockNode block : region.blocks()) {
            writeBlock(output, block, ids, styles, pageValues, centralNavigation, true);
        }
        output.append("</div>");
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
                .append("\" href=\"#").append(escapeAttribute(bookmark.effectiveTargetId()))
                    .append("\"></bookmark>");
        }
        output.append("</bookmarks>");
    }

    /** 按主字体、普通补充字体、最终回退字体的顺序写入 CSS。 */
    private void writeFontFamilies(StringBuilder output, io.github.leylaragg.letool.print.document.DocumentOptions
        options) {
        List<String> selectedFamilies = options.defaultFontFamily().isEmpty() ? fontFamilies
            : List.of(options.defaultFontFamily());
        for (int index = 0; index < selectedFamilies.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append('\'').append(selectedFamilies.get(index)).append('\'');
        }
        if (!fontFamilies.isEmpty()) {
            output.append(", ");
        }
        output.append("sans-serif");
    }

    /** 根据节点的跨格式语义写入固定块级标签。 */
    private void writeBlock(
            StringBuilder output,
            BlockNode block,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation,
            boolean repeatedRegion) {
        if (block instanceof SectionNode section) {
            output.append("<section");
            writeId(output, section.id());
            output.append('>');
            section.children().forEach(child -> writeBlock(
                    output, child, ids, styles, pageValues, centralNavigation, repeatedRegion));
            output.append("</section>");
        } else if (block instanceof HeadingNode heading) {
            String tag = "h" + heading.level();
            output.append('<').append(tag);
            writeId(output, ids.targetId(heading));
            writeClass(output, styles.paragraphClass(heading.styleName()));
            output.append('>');
            heading.children().forEach(child -> writeInline(
                    output, child, ids, styles, pageValues, centralNavigation));
            output.append("</").append(tag).append('>');
        } else if (block instanceof ParagraphNode paragraph) {
            output.append("<p");
            writeId(output, paragraph.id());
            writeClass(output, styles.paragraphClass(paragraph.styleName()));
            output.append('>');
            paragraph.children().forEach(child -> writeInline(
                    output, child, ids, styles, pageValues, centralNavigation));
            output.append("</p>");
        } else if (block instanceof io.github.leylaragg.letool.print.document.node.ContentsTitleNode title) {
            writeBlock(output, new ParagraphNode(title.id(), title.styleName(), List.of(new TextNode(title.title(),
                ""))),
                ids, styles, pageValues, centralNavigation, repeatedRegion);
        } else if (block instanceof io.github.leylaragg.letool.print.document.node.ContentsEntryNode entry) {
            writeContentsEntry(output, entry, ids, styles, pageValues, centralNavigation);
        } else if (block instanceof TableNode table) {
            writeTable(output, table, ids, styles, pageValues, centralNavigation, repeatedRegion);
        } else if (block == PageBreakNode.INSTANCE) {
            output.append("<div class=\"page-break\"></div>");
        } else if (block instanceof FreeTextNode freeText) {
            writeFreeText(output, freeText, styles, false);
        } else if (block instanceof AnnotationNode || block instanceof
            io.github.leylaragg.letool.print.document.node.BodyCaptionNode) {
            // 批注正文只进入 PDF 对象，不在页面正文中重复显示。
            return;
        } else if (block instanceof TableOfContentsNode) {
            throw PrintValidationException.invalidDocument("目录节点必须由 PDF 目录渲染器处理");
        } else {
            throw unsupported(block);
        }
    }

    private void writeContentsEntry(StringBuilder output,
        io.github.leylaragg.letool.print.document.node.ContentsEntryNode entry, PdfRenderIds ids,
        PdfStyleCatalog styles, PageValues pageValues, boolean centralNavigation) {
        io.github.leylaragg.letool.print.document.node.TableOfContentsLayout layout = entry.layout();
        String sourceId = ids.sourceId(entry.link());
        output.append("<div id=\"").append(sourceId)
            .append("\" data-toc-entry=\"true\" style=\"position:relative;page-break-" +
            "inside:avoid;min-height:")
            .append(entry.level() == 1 ? layout.groupHeight() : layout.entryHeight()).append("pt;");
        if (entry.keepWithNext()) {
            output.append("page-break-after:avoid;");
        }
        output.append("\"><p id=\"").append(sourceId).append("-label\" style=\"margin:0 36pt 0 ")
            .append((entry.level() - 1) * layout.levelIndent()).append("pt;word-wrap:break-word;\"><span");
        writeClass(output, styles.textClass(layout.textStyleName()));
        output.append('>');
        entry.link().label().forEach(child -> writeInline(output, child, ids, styles, pageValues, centralNavigation));
        output.append("</span></p><p id=\"").append(sourceId).append("-page\" style=\"position:absolute;right:")
            .append(layout.pageInset()).append("pt;margin:0;\"><span");
        writeClass(output, styles.textClass(layout.textStyleName()));
        output.append('>').append(escapeText(entry.pageNumber())).append("</span></p></div>");
    }

    /**
     * 保留表头与表体分区；已处于页眉、页脚或表头中的内表随重复区域整体移动。
     * 外壳只消除行内表格的基线留白，内表恢复正常行高，命名文本样式继续生效。
     */
    private void writeTable(
            StringBuilder output,
            TableNode table,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation,
            boolean repeatedRegion) {
        if (repeatedRegion) {
            output.append("<div class=\"lt-repeated-table\">");
        }
        output.append("<table");
        writeId(output, table.id());
        writeClass(output, styles.tableClass(table.styleName()));
        output.append('>');
        writeColumns(output, table, styles);
        if (table.headerRowCount() > 0) {
            output.append("<thead>");
            writeRows(output, table.rows().subList(0, table.headerRowCount()), true,
                    ids, styles, pageValues, centralNavigation, true);
            output.append("</thead>");
        }
        if (table.headerRowCount() < table.rows().size()) {
            output.append("<tbody>");
            writeRows(output, table.rows().subList(table.headerRowCount(), table.rows().size()), false,
                    ids, styles, pageValues, centralNavigation, repeatedRegion);
            output.append("</tbody>");
        }
        output.append("</table>");
        if (repeatedRegion) {
            output.append("</div>");
        }
    }

    /** 写入一个连续表格分区中的行。 */
    private void writeRows(
            StringBuilder output,
            List<TableRow> rows,
            boolean header,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation,
            boolean repeatedRegion) {
        for (TableRow row : rows) {
            output.append("<tr");
            if (row.minimumHeightPoints() > 0 || row.keepWithNext()) {
                output.append(" style=\"height:").append(row.minimumHeightPoints()).append("pt;");
                if (row.keepWithNext()) {
                    output.append("page-break-after:avoid;");
                }
                output.append('"');
            }
            output.append('>');
            for (TableCell cell : row.cells()) {
                writeCell(output, cell, header, ids, styles, pageValues, centralNavigation, repeatedRegion);
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
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation,
            boolean repeatedRegion) {
        String tag = header ? "th" : "td";
        output.append('<').append(tag);
        writeClass(output, styles.cellClass(cell.styleName()));
        if (cell.repeatOnSplit()) {
            output.append(" data-repeat-on-split=\"true\"");
        }
        if (cell.rowSpan() > 1) {
            output.append(" rowspan=\"").append(cell.rowSpan()).append('\"');
        }
        if (cell.colSpan() > 1) {
            output.append(" colspan=\"").append(cell.colSpan()).append('\"');
        }
        output.append('>');
        cell.content().forEach(block -> writeBlock(
                output, block, ids, styles, pageValues, centralNavigation, repeatedRegion));
        output.append("</").append(tag).append('>');
    }

    /** 根据节点语义写入转义文本、书签目标或内部链接。 */
    private void writeInline(
            StringBuilder output,
            InlineNode inline,
            PdfRenderIds ids,
            PdfStyleCatalog styles,
            PageValues pageValues,
            boolean centralNavigation) {
        if (inline instanceof io.github.leylaragg.letool.print.document.node.InlineGroupNode group) {
            if (group.widthPoints() > 0 || group.keepTogether()) {
                output.append("<span data-keep-together=\"").append(group.keepTogether())
                        .append("\" data-auto-group=\"").append(group.widthPoints() == 0)
                        .append("\" style=\"display:inline-block;white-space:nowrap;");
                if (group.widthPoints() > 0) {
                    output.append("text-align:center;width:").append(group.widthPoints()).append("pt;");
                }
                output.append("\">");
            }
            output.append(group.underline() ? "<span data-native-underline=\"true\">" : "<span>");
            group.children().forEach(child -> writeInline(output, child, ids, styles, pageValues, centralNavigation));
            output.append("</span>");
            if (group.widthPoints() > 0 || group.keepTogether()) {
                output.append("</span>");
            }
        } else if (inline instanceof io.github.leylaragg.letool.print.document.node.ShapeNode shape) {
            output.append("<object type=\"letool-shape\" data-kind=\"").append(shape.kind())
                .append("\" data-stroke=\"").append(shape.strokePoints()).append("\" data-selected=\"")
                .append(shape.selected())
                .append("\" data-cells=\"").append(shape.cells()).append("\" data-mark=\"").append(shape
                    .markPoints()).append("\" style=\"display:inline-block;width:")
                .append(shape.widthPoints()).append("pt;height:").append(shape.heightPoints()).append(shape
                .kind() == io.github.leylaragg.letool.print.document.node.ShapeNode.Kind.U_CELLS ?
                "pt;vertical-align:-2pt;\"/>" : "pt;vertical-align:middle;\"/>");
        } else if (inline instanceof FreeTextNode freeText) {
            writeFreeText(output, freeText, styles, true);
        } else if (inline instanceof TextNode text) {
            String className = styles.textClass(text.styleName());
            if (className.isEmpty()) {
                output.append(escapeText(text.text()));
            } else {
                output.append("<span class=\"").append(className).append("\">")
                        .append(escapeText(text.text())).append("</span>");
            }
        } else if (inline == LineBreakNode.INSTANCE) {
            output.append("<br/>");
        } else if (inline instanceof BookmarkNode bookmark) {
            if (bookmark.targetId().isEmpty()) {
                output.append("<span id=\"").append(escapeAttribute(bookmark.id()))
                    .append("\" class=\"bookmark\">").append(escapeText(bookmark.label()))
                    .append("</span>");
            }
        } else if (inline instanceof InternalLinkNode link) {
            if (centralNavigation) {
                output.append("<span id=\"").append(escapeAttribute(ids.sourceId(link)))
                        .append("\" class=\"internal-link\">");
                link.label().forEach(label -> writeInline(
                        output, label, ids, styles, pageValues, true));
                output.append("</span>");
            } else {
                output.append("<a href=\"#").append(escapeAttribute(ids.targetId(link.targetId()))).append("\">");
                link.label().forEach(label -> writeInline(
                        output, label, ids, styles, pageValues, false));
                output.append("</a>");
            }
        } else if (inline instanceof PageNumberNode pageNumber) {
            if (pageValues.showPageNumber) {
                writeGeneratedText(output, "lt-page-number",
                        styles.textClass(pageNumber.styleName()), null);
            }
        } else if (inline instanceof PageCountNode pageCount) {
            writeGeneratedText(output, "", styles.textClass(pageCount.styleName()),
                    Integer.toString(pageValues.totalPages));
        } else {
            throw unsupported(inline);
        }
    }

    /** 先交给原生布局测量父容器宽度，正文随后由完整外观承载。 */
    private void writeFreeText(StringBuilder output, FreeTextNode node, PdfStyleCatalog styles, boolean inline) {
        String key = "box-" + output.length();
        output.append("<object type=\"letool-free-text\"");
        writeId(output, node.id());
        writeClass(output, node.textStyleName().isEmpty()
                ? PdfStyleCatalog.DEFAULT_FREE_TEXT_CLASS : styles.textClass(node.textStyleName()));
        output.append(" data-free-text-key=\"").append(key)
                .append("\" data-text-style=\"").append(escapeAttribute(node.textStyleName()))
                .append("\" data-box-style=\"").append(escapeAttribute(node.boxStyleName()))
                .append("\" data-author=\"").append(escapeAttribute(node.author()))
                .append("\" data-width-mode=\"").append(node.widthMode())
                .append("\" data-inline=\"").append(inline).append('"');
        DocumentColor background = node.background();
        if (background != null) {
            output.append(" data-background=\"").append(background.red()).append(',')
                    .append(background.green()).append(',').append(background.blue()).append('"');
        }
        output.append(" style=\"display:").append(inline ? "inline-block" : "block")
                .append(";width:").append(inline ? "1pt" : "100%")
                .append(";height:1pt;page-break-inside:avoid;vertical-align:middle;\">")
                .append(escapeText(node.content())).append("</object>");
    }

    /** 写入页码占位或已经规划好的逻辑总页数。 */
    private void writeGeneratedText(
            StringBuilder output,
            String generatedClass,
            String styleClass,
            String text) {
        String classes = generatedClass;
        if (!styleClass.isEmpty()) {
            classes = classes.isEmpty() ? styleClass : classes + " " + styleClass;
        }
        if (classes.isEmpty() && text != null) {
            output.append(text);
            return;
        }
        output.append("<span");
        writeClass(output, classes);
        output.append('>');
        if (text != null) {
            output.append(text);
        }
        output.append("</span>");
    }

    /** 固定布局表格通过安全列宽值写入 colgroup。 */
    private void writeColumns(
            StringBuilder output, TableNode table, PdfStyleCatalog styles) {
        if (table.styleName().isEmpty()) {
            return;
        }
        List<String> widths = styles.tableColumnWidths(table.styleName());
        if (widths.isEmpty()) {
            return;
        }
        output.append("<colgroup>");
        for (String width : widths) {
            output.append("<col style=\"width:").append(width).append(";\"/>");
        }
        output.append("</colgroup>");
    }

    /** 非空逻辑 ID 才会进入输出。 */
    private void writeId(StringBuilder output, String id) {
        if (!id.isEmpty()) {
            output.append(" id=\"").append(escapeAttribute(id)).append('\"');
        }
    }

    /** 非空框架类名才会进入输出，模板样式名不会经过这里。 */
    private void writeClass(StringBuilder output, String className) {
        if (!className.isEmpty()) {
            output.append(" class=\"").append(className).append('"');
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

    /** 当前序列写出页码节点时使用的稳定值。 */
    private static final class PageValues {

        /** 是否显示当前逻辑页码。 */
        private final boolean showPageNumber;

        /** 文档中计入页码规则的物理页数总和。 */
        private final int totalPages;

        /** 保存当前分页轮次需要写入 XHTML 的页码值。 */
        private PageValues(boolean showPageNumber, int totalPages) {
            this.showPageNumber = showPageNumber;
            this.totalPages = totalPages;
        }
    }
}
