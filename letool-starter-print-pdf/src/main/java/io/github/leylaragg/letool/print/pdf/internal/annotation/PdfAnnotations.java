package io.github.leylaragg.letool.print.pdf.internal.annotation;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfNavigationWriter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.Map;

/**
 * 批注包的内部协作入口，封装完整字体测量结果与最终外观写入。
 *
 * <p>渲染编排不直接依赖文字片段、字形或中间文本框数据，全部外观规则仍由原写入器维护。</p>
 *
 * @author leyland
 */
public final class PdfAnnotations {
    /** 最终文档允许的采集批注数量上限，不包含导航链接。 */
    public static final int MAX_ANNOTATIONS = 1_000;

    /** 用原生链接记录文本框的真实物理位置，最终写入时统一替换。 */
    static final String MARKER_PREFIX = "letool-free-text:";

    private final PdfAnnotationWriter writer;

    /** 为当前渲染器配置同一份字体目录。 */
    public PdfAnnotations(PdfFontCatalog fonts) {
        this.writer = new PdfAnnotationWriter(fonts);
    }

    /** 使用最终全局目标位置写入文档中声明的传统批注。 */
    public void writeMerged(PDDocument pdf, DocumentModel document,
            Map<String, PdfNavigationWriter.GlobalPosition> targets) throws IOException {
        writer.writeMerged(pdf, writer.collect(document), targets);
    }

    /** 创建尚未执行文本框测量的视图，保持原 XHTML 不变。 */
    public static Prepared empty(String xhtml) {
        return new Prepared(new PdfFreeTextLayout.Prepared(xhtml, Map.of()));
    }

    /** 按原生字体度量准备不可裁切的流式文本框。 */
    public static Prepared prepare(PdfBoxRenderer renderer, String xhtml, StyleSheet styles,
            DocumentOptions options, PdfFontCatalog fonts) throws IOException {
        return new Prepared(PdfFreeTextLayout.prepare(renderer, xhtml, styles, options, fonts));
    }

    /** 把原生链接占位替换为使用相同测量数据的完整 FreeText 外观。 */
    public static void writeFreeText(PDDocument pdf, Prepared prepared) throws IOException {
        PdfFreeTextWriter.write(pdf, prepared.source.boxes());
    }

    /** @return 指定文本框位置标记的内部链接地址 */
    public static String markerFor(String key) {
        return MARKER_PREFIX + key;
    }

    /** 不透明的文本框测量快照；包外只读取修正视图和是否需要写入外观。 */
    public static final class Prepared {
        private final PdfFreeTextLayout.Prepared source;

        private Prepared(PdfFreeTextLayout.Prepared source) {
            this.source = source;
        }

        /** @return 已按真实字体测量修正的 XHTML */
        public String xhtml() {
            return source.xhtml();
        }

        /** @return 当前视图是否含需要写入外观的文本框 */
        public boolean hasBoxes() {
            return !source.boxes().isEmpty();
        }
    }
}
