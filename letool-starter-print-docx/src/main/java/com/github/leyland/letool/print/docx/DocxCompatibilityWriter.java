package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.DocumentTraversal;
import com.github.leyland.letool.print.document.PageOrientation;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.parts.WordprocessingML.EndnotesPart;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.CTEndnotes;
import org.docx4j.wml.CTFtnEdn;
import org.docx4j.wml.CTFtnEdnRef;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblPr;
import org.docx4j.wml.TblWidth;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 为 DOCX 暂时无法等价表达的节点写入可读替代内容。
 *
 * @author leyland
 */
final class DocxCompatibilityWriter {

    /** 图片资源接入前，用单格表格在原位置保留可识别的占位。 */
    Tbl imagePlaceholder(
            DocxRenderContext context, ImageNode image, List<String> anchorNames) {
        Tbl table = context.factory().createTbl();
        TblPr properties = context.factory().createTblPr();
        TblWidth width = context.factory().createTblWidth();
        width.setType(TblWidth.TYPE_DXA);
        width.setW(BigInteger.valueOf(placeholderWidth(context, image)));
        properties.setTblW(width);
        table.setTblPr(properties);

        Tr row = context.factory().createTr();
        Tc cell = context.factory().createTc();
        P paragraph = context.factory().createP();
        if (!image.altText().isEmpty()) {
            appendText(context, paragraph, image.altText());
        }
        cell.getContent().add(paragraph);
        row.getContent().add(cell);
        table.getContent().add(row);
        new DocxNavigationWriter().anchorFirstParagraph(context, table, anchorNames);
        context.recordDegradation("image");
        return table;
    }

    /** 将定位批注集中写为尾注，并把引用放到目标段落。 */
    void writeAnnotations(DocxRenderContext context) throws Exception {
        List<AnnotationNode> annotations = new ArrayList<>();
        for (DocumentNode node : DocumentTraversal.depthFirst(context.document())) {
            if (node instanceof AnnotationNode annotation) {
                annotations.add(annotation);
            }
        }
        if (annotations.isEmpty()) {
            return;
        }

        EndnotesPart endnotesPart = new EndnotesPart();
        CTEndnotes endnotes = context.factory().createCTEndnotes();
        endnotesPart.setJaxbElement(endnotes);
        context.wordPackage().getMainDocumentPart().addTargetPart(endnotesPart);
        for (int index = 0; index < annotations.size(); index++) {
            AnnotationNode annotation = annotations.get(index);
            BigInteger id = BigInteger.valueOf(index + 1L);
            endnotes.getEndnote().add(endnote(context, annotation, id));
            addReference(context, annotation, id);
            context.recordDegradation("annotation");
        }
    }

    /** 把图片宽度限制在当前页面可用区域内。 */
    private long placeholderWidth(DocxRenderContext context, ImageNode image) {
        var layout = context.document().pageLayout();
        int pageWidth = layout.orientation() == PageOrientation.PORTRAIT
                ? layout.pageSize().widthMicrometers()
                : layout.pageSize().heightMicrometers();
        long availableWidth = (long) pageWidth
                - layout.margins().leftMicrometers()
                - layout.margins().rightMicrometers();
        int boundedWidth = (int) Math.min(image.widthMicrometers(), availableWidth);
        return DocxUnits.micrometersToTwips(boundedWidth);
    }

    /** 创建保留作者和正文的单条尾注。 */
    private CTFtnEdn endnote(
            DocxRenderContext context, AnnotationNode annotation, BigInteger id) {
        CTFtnEdn endnote = context.factory().createCTFtnEdn();
        endnote.setId(id);
        P paragraph = context.factory().createP();
        if (!annotation.author().isEmpty()) {
            appendText(context, paragraph, annotation.author() + "：");
        }
        appendText(context, paragraph, annotation.content());
        endnote.getContent().add(paragraph);
        return endnote;
    }

    /** 在目标段落末尾追加尾注引用。 */
    private void addReference(
            DocxRenderContext context, AnnotationNode annotation, BigInteger id) {
        String targetName = context.renderIds().targetName(annotation.targetId());
        P target = findBookmarkParagraph(
                context.wordPackage().getMainDocumentPart().getJaxbElement(), targetName);
        if (target == null) {
            throw new IllegalStateException("DOCX 批注目标没有生成书签");
        }
        CTFtnEdnRef reference = context.factory().createCTFtnEdnRef();
        reference.setId(id);
        R run = context.factory().createR();
        run.getContent().add(context.factory().createREndnoteReference(reference));
        target.getContent().add(run);
    }

    /** 在生成的正文结构中寻找包含指定书签的段落。 */
    private P findBookmarkParagraph(Object value, String bookmarkName) {
        Object unwrapped = XmlUtils.unwrap(value);
        if (unwrapped instanceof P paragraph && hasBookmark(paragraph, bookmarkName)) {
            return paragraph;
        }
        if (unwrapped instanceof ContentAccessor accessor) {
            for (Object child : accessor.getContent()) {
                P paragraph = findBookmarkParagraph(child, bookmarkName);
                if (paragraph != null) {
                    return paragraph;
                }
            }
        }
        return null;
    }

    /** 判断段落是否包含指定安全书签名。 */
    private boolean hasBookmark(P paragraph, String bookmarkName) {
        for (Object content : paragraph.getContent()) {
            Object unwrapped = XmlUtils.unwrap(content);
            if (unwrapped instanceof CTBookmark bookmark
                    && bookmarkName.equals(bookmark.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 追加一个独立文本 run，作者与正文由此保持分离。 */
    private void appendText(DocxRenderContext context, P paragraph, String value) {
        Text text = context.factory().createText();
        text.setValue(value);
        text.setSpace("preserve");
        R run = context.factory().createR();
        run.getContent().add(context.factory().createRT(text));
        paragraph.getContent().add(run);
    }
}
