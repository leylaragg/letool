package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.InlineNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import org.docx4j.wml.Br;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.STBrType;

import java.util.ArrayList;
import java.util.List;

/**
 * 按文档模型顺序写入基础块节点和行内文本。
 *
 * @author leyland
 */
final class DocxBodyWriter {

    /** 导航节点和普通文本共用同一行内写入器。 */
    private final DocxNavigationWriter navigationWriter = new DocxNavigationWriter();

    /** 将顶层块节点写入正文，逻辑章节在这里透明展开。 */
    void write(DocxRenderContext context, List<BlockNode> blocks) {
        List<Object> target = context.wordPackage().getMainDocumentPart().getContent();
        writeInto(context, target, blocks);
    }

    /** 将一组块节点写入正文或表格单元格。 */
    void writeInto(DocxRenderContext context, List<Object> target, List<BlockNode> blocks) {
        for (BlockNode block : blocks) {
            writeBlock(context, target, block, List.of());
        }
    }

    /** 根据块节点语义选择对应的 WordprocessingML 结构。 */
    private void writeBlock(
            DocxRenderContext context,
            List<Object> target,
            BlockNode block,
            List<String> inheritedAnchors) {
        if (block instanceof SectionNode section) {
            List<String> sectionAnchors = anchors(
                    context, inheritedAnchors, section.id(), null);
            for (int index = 0; index < section.children().size(); index++) {
                writeBlock(context, target, section.children().get(index),
                        index == 0 ? sectionAnchors : List.of());
            }
        } else if (block instanceof HeadingNode heading) {
            P paragraph = paragraph(context, "Heading" + heading.level(), heading.children());
            navigationWriter.anchorParagraph(context, paragraph,
                    anchors(context, inheritedAnchors, heading.id(), heading));
            target.add(paragraph);
        } else if (block instanceof ParagraphNode paragraph) {
            P result = paragraph(context, null, paragraph.children());
            navigationWriter.anchorParagraph(context, result,
                    anchors(context, inheritedAnchors, paragraph.id(), null));
            target.add(result);
        } else if (block instanceof PageBreakNode) {
            P result = pageBreak(context);
            navigationWriter.anchorParagraph(context, result, inheritedAnchors);
            target.add(result);
        } else if (block instanceof TableNode table) {
            List<String> tableAnchors = anchors(
                    context, inheritedAnchors, table.id(), null);
            target.add(new DocxTableWriter().write(context, table, this, tableAnchors));
        } else if (block instanceof TableOfContentsNode tableOfContents) {
            navigationWriter.writeTableOfContents(context, target, tableOfContents);
        } else if (block instanceof ImageNode image) {
            List<String> imageAnchors = anchors(
                    context, inheritedAnchors, image.id(), null);
            target.add(new DocxCompatibilityWriter().imagePlaceholder(
                    context, image, imageAnchors));
        }
    }

    /** 创建普通段落或标题段落，并保留空段落。 */
    private P paragraph(
            DocxRenderContext context, String styleId, List<InlineNode> children) {
        P paragraph = context.factory().createP();
        if (styleId != null) {
            PPr properties = context.factory().createPPr();
            PPrBase.PStyle style = context.factory().createPPrBasePStyle();
            style.setVal(styleId);
            properties.setPStyle(style);
            paragraph.setPPr(properties);
        }
        navigationWriter.appendInlines(context, paragraph, children);
        return paragraph;
    }

    /** 汇总外层章节和当前块自己的书签名称。 */
    private List<String> anchors(
            DocxRenderContext context,
            List<String> inherited,
            String logicalId,
            HeadingNode heading) {
        List<String> names = new ArrayList<>(inherited);
        if (heading != null) {
            names.add(context.renderIds().headingName(heading));
        } else if (!logicalId.isEmpty()) {
            names.add(context.renderIds().targetName(logicalId));
        }
        return names;
    }

    /** 创建只承担显式分页语义的独立段落。 */
    private P pageBreak(DocxRenderContext context) {
        P paragraph = context.factory().createP();
        R run = context.factory().createR();
        Br pageBreak = context.factory().createBr();
        pageBreak.setType(STBrType.PAGE);
        run.getContent().add(pageBreak);
        paragraph.getContent().add(run);
        return paragraph;
    }
}
