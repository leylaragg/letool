package io.github.leylaragg.letool.print.pdf.internal.layout;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BodyCaptionNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

/**
 * 原生布局的内部协作入口，统一区域定位、分段、坐标采集和追加绘制。
 *
 * <p>调用方只接收脱离原生排版器生命周期的快照，具体盒子遍历与拆分算法保持包级。</p>
 *
 * @author leyland
 */
public final class PdfLayoutProcessor {
    /** 用于测量运行页眉的固定元素 ID。 */
    public static final String HEADER_REGION_ID = "letool-page-header-region";

    /** 用于测量运行页脚的固定元素 ID。 */
    public static final String FOOTER_REGION_ID = "letool-page-footer-region";

    private PdfLayoutProcessor() {
    }

    /** 按实测高度修正页面区域位置，不重新定义模板样式。 */
    public static String adjust(PdfBoxRenderer renderer, String xhtml, PageLayout layout) throws IOException {
        return PdfNativeLayout.adjust(renderer, xhtml, layout);
    }

    /** 校正首个需要移动或拆分的行，调用方随后使用新视图重新测量。 */
    public static String fragment(PdfBoxRenderer renderer, String xhtml) throws IOException {
        return PdfNativeFragmenter.fragment(renderer, xhtml);
    }

    /** 保存目标及链接源的真实位置，仅依赖 ID 集合，不持有导航编排状态。 */
    public static PdfLayoutSnapshot snapshot(PdfBoxRenderer renderer, Collection<String> targetIds,
            Collection<String> sourceIds) {
        return new PdfLayoutSnapshotter().snapshot(renderer, targetIds, sourceIds);
    }

    /** 收集本轮绘制标记，包外不直接操作内部线段集合。 */
    public static Drawing collectDrawing(PdfBoxRenderer renderer) {
        return new Drawing(PdfNativeMarks.collect(renderer));
    }

    /** 在已生成页面上追加实测下划线和正文图例。 */
    public static void writeDrawing(PDDocument pdf, Drawing drawing, List<BodyCaptionNode> captions,
            StyleSheet styles, DocumentOptions options, PageLayout layout, PdfFontCatalog fonts) throws IOException {
        PdfNativeDrawing.write(pdf, drawing.marks, captions, styles, options, layout, fonts);
    }

    /** 不透明的追加绘制输入，只允许检查是否存在下划线及交回本入口消费。 */
    public static final class Drawing {
        private final PdfNativeMarks.Marks marks;

        private Drawing(PdfNativeMarks.Marks marks) {
            this.marks = marks;
        }

        /** @return 当前单元是否包含需要追加绘制的下划线 */
        public boolean hasUnderlines() {
            return !marks.underlines().isEmpty();
        }
    }

    /** 将已修改的 DOM 转回 XHTML，供下一轮原生布局使用。 */
    public static String serialize(Document document) throws IOException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (TransformerException exception) {
            throw new IOException("受控原生分段视图保存失败", exception);
        }
    }

}
