package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.DocumentTraversal;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InlineNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import org.docx4j.XmlUtils;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.CTMarkupRange;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.FldChar;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.STFldCharType;
import org.docx4j.wml.Text;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 写入书签范围和文档内部超链接。
 *
 * @author leyland
 */
final class DocxNavigationWriter {

    /** 按行内节点顺序写入文本、显式书签和内部链接。 */
    void appendInlines(DocxRenderContext context, P paragraph, List<InlineNode> children) {
        appendInlines(context, paragraph.getContent(), children);
    }

    /** 把一个或多个逻辑目标锚定到同一段落。 */
    void anchorParagraph(DocxRenderContext context, P paragraph, List<String> names) {
        int startOffset = 0;
        for (String name : names) {
            BigInteger id = context.renderIds().nextBookmarkId();
            CTBookmark start = context.factory().createCTBookmark();
            start.setId(id);
            start.setName(name);
            paragraph.getContent().add(
                    startOffset++, context.factory().createPBookmarkStart(start));

            CTMarkupRange end = context.factory().createCTMarkupRange();
            end.setId(id);
            paragraph.getContent().add(context.factory().createPBookmarkEnd(end));
        }
    }

    /** 将表格或其他块容器的目标锚定到首个实际段落。 */
    void anchorFirstParagraph(DocxRenderContext context, Object container, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        P paragraph = firstParagraph(container);
        if (paragraph != null) {
            anchorParagraph(context, paragraph, names);
        }
    }

    /** 写入 Word 目录域和一份可立即阅读的标题缓存。 */
    void writeTableOfContents(
            DocxRenderContext context, List<Object> target, TableOfContentsNode tableOfContents) {
        if (tableOfContents.title() != null) {
            P title = context.factory().createP();
            appendText(context, title.getContent(), tableOfContents.title());
            target.add(title);
        }

        P fieldStart = context.factory().createP();
        fieldStart.getContent().add(fieldCharacter(context, STFldCharType.BEGIN));
        R instructionRun = context.factory().createR();
        Text instruction = context.factory().createText();
        instruction.setSpace("preserve");
        instruction.setValue(" TOC \\o \"" + tableOfContents.minLevel() + "-"
                + tableOfContents.maxLevel() + "\" \\h \\z \\u ");
        instructionRun.getContent().add(context.factory().createRInstrText(instruction));
        fieldStart.getContent().add(instructionRun);
        fieldStart.getContent().add(fieldCharacter(context, STFldCharType.SEPARATE));
        target.add(fieldStart);

        for (HeadingNode heading : headingsAfter(context, tableOfContents)) {
            P cachedHeading = context.factory().createP();
            PPr properties = context.factory().createPPr();
            PPrBase.PStyle style = context.factory().createPPrBasePStyle();
            style.setVal("Heading" + heading.level());
            properties.setPStyle(style);
            cachedHeading.setPPr(properties);
            P.Hyperlink link = context.factory().createPHyperlink();
            link.setAnchor(context.renderIds().headingName(heading));
            appendText(context, link.getContent(), visibleText(heading));
            cachedHeading.getContent().add(context.factory().createPHyperlink(link));
            target.add(cachedHeading);
        }

        P fieldEnd = context.factory().createP();
        fieldEnd.getContent().add(fieldCharacter(context, STFldCharType.END));
        target.add(fieldEnd);
        BooleanDefaultTrue updateFields = new BooleanDefaultTrue();
        updateFields.setVal(true);
        context.wordPackage().getMainDocumentPart().getDocumentSettingsPart()
                .getJaxbElement().setUpdateFields(updateFields);
        context.requireFieldUpdate();
    }

    /** 找出目录声明之后且层级匹配的标题。 */
    private List<HeadingNode> headingsAfter(
            DocxRenderContext context, TableOfContentsNode tableOfContents) {
        List<HeadingNode> headings = new ArrayList<>();
        boolean afterContents = false;
        for (DocumentNode node : DocumentTraversal.depthFirst(context.document())) {
            if (node == tableOfContents) {
                afterContents = true;
            } else if (afterContents && node instanceof HeadingNode heading
                    && heading.level() >= tableOfContents.minLevel()
                    && heading.level() <= tableOfContents.maxLevel()) {
                headings.add(heading);
            }
        }
        return headings;
    }

    /** 创建复杂域的开始、分隔或结束字符。 */
    private R fieldCharacter(DocxRenderContext context, STFldCharType type) {
        FldChar fieldCharacter = context.factory().createFldChar();
        fieldCharacter.setFldCharType(type);
        R run = context.factory().createR();
        run.getContent().add(context.factory().createRFldChar(fieldCharacter));
        return run;
    }

    /** 提取目录缓存中需要展示的标题文字。 */
    private String visibleText(HeadingNode heading) {
        StringBuilder text = new StringBuilder();
        appendVisibleText(text, heading.children());
        return text.toString();
    }

    /** 递归拼接标题中的普通文字、书签标签和链接标签。 */
    private void appendVisibleText(StringBuilder text, List<InlineNode> children) {
        for (InlineNode child : children) {
            if (child instanceof TextNode value) {
                text.append(value.text());
            } else if (child instanceof BookmarkNode bookmark) {
                text.append(bookmark.label());
            } else if (child instanceof InternalLinkNode link) {
                appendVisibleText(text, link.label());
            }
        }
    }

    /** 在已生成的块容器中寻找第一个可承载书签的段落。 */
    private P firstParagraph(Object value) {
        Object unwrapped = XmlUtils.unwrap(value);
        if (unwrapped instanceof P paragraph) {
            return paragraph;
        }
        if (unwrapped instanceof ContentAccessor accessor) {
            for (Object child : accessor.getContent()) {
                P paragraph = firstParagraph(child);
                if (paragraph != null) {
                    return paragraph;
                }
            }
        }
        return null;
    }

    /** 递归写入普通行内容或超链接标签。 */
    private void appendInlines(
            DocxRenderContext context, List<Object> target, List<InlineNode> children) {
        for (InlineNode child : children) {
            if (child instanceof TextNode text) {
                appendText(context, target, text.text());
            } else if (child instanceof BookmarkNode bookmark) {
                appendBookmark(context, target, bookmark);
            } else if (child instanceof InternalLinkNode link) {
                P.Hyperlink hyperlink = context.factory().createPHyperlink();
                hyperlink.setAnchor(context.renderIds().targetName(link.targetId()));
                appendInlines(context, hyperlink.getContent(), link.label());
                target.add(context.factory().createPHyperlink(hyperlink));
            }
        }
    }

    /** 在标签文字两侧写入显式书签范围。 */
    private void appendBookmark(
            DocxRenderContext context, List<Object> target, BookmarkNode bookmark) {
        BigInteger id = context.renderIds().nextBookmarkId();
        CTBookmark start = context.factory().createCTBookmark();
        start.setId(id);
        start.setName(context.renderIds().targetName(bookmark.id()));
        target.add(context.factory().createPBookmarkStart(start));
        appendText(context, target, bookmark.label());
        CTMarkupRange end = context.factory().createCTMarkupRange();
        end.setId(id);
        target.add(context.factory().createPBookmarkEnd(end));
    }

    /** 把文本换行拆成 Word 的显式换行元素。 */
    private void appendText(DocxRenderContext context, List<Object> target, String value) {
        R run = context.factory().createR();
        String[] lines = value.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                run.getContent().add(context.factory().createBr());
            }
            Text text = context.factory().createText();
            text.setValue(lines[index]);
            text.setSpace("preserve");
            run.getContent().add(context.factory().createRT(text));
        }
        target.add(run);
    }
}
