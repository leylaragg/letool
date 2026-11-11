package io.github.leylaragg.letool.print.pdf.internal.annotation;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.font.PdfFontResources;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfNavigationWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;

import java.io.IOException;
import java.util.Map;

/**
 * 批注包的内部协作入口，负责文本框测量、批注写入和额度校验。
 *
 * <p>渲染编排不直接依赖文字片段、字形或中间文本框数据，全部外观规则仍由原写入器维护。</p>
 *
 * @author leyland
 */
public final class PdfAnnotations {
    /** 兼容旧内部调用的默认值，执行额度以当前 RenderOptions 为准。 */
    public static final int MAX_ANNOTATIONS = RenderOptions.DEFAULT_MAX_ANNOTATIONS;

    /** 用原生链接记录文本框的真实物理位置，最终写入时统一替换。 */
    static final String MARKER_PREFIX = "letool-free-text:";

    /** 单元超限即可提前停止，此时尚未得到整份文档的物理总数。 */
    static final String UNIT_ANNOTATION_SCOPE = "当前排版单元物理数（整份文档至少达到该数）";

    private final PdfAnnotationWriter writer;

    /**
     * 使用默认字节预算创建批注处理入口。
     *
     * @param fonts 宿主字体目录
     */
    public PdfAnnotations(PdfFontCatalog fonts) {
        this(fonts, new PdfFontResources(RenderOptions.DEFAULT_MAX_FONT_CACHE_BYTES));
    }

    /**
     * 创建与正文排版共享字体资源的批注处理入口。
     *
     * @param fonts 宿主字体目录
     * @param resources 本次渲染共用的字体资源
     */
    public PdfAnnotations(PdfFontCatalog fonts, PdfFontResources resources) {
        this.writer = new PdfAnnotationWriter(fonts, resources);
    }

    /**
     * 按默认额度写入文档中声明的传统批注。
     *
     * @param pdf 已完成页面合并的 PDF
     * @param document 含传统批注声明的文档模型
     * @param targets 目标节点在合并后 PDF 中的全局位置
     * @throws IOException 批注读取或外观写入失败时抛出
     * @throws PrintValidationException 超出额度或批注位置无效时抛出
     */
    public void writeMerged(PDDocument pdf, DocumentModel document,
            Map<String, PdfNavigationWriter.GlobalPosition> targets) throws IOException {
        writeMerged(pdf, document, targets, RenderOptions.defaults());
    }

    /**
     * 按合并后的目标位置写入传统批注，和已写入的文本框合计额度。
     *
     * @param pdf 已完成页面合并的 PDF
     * @param document 含传统批注声明的文档模型
     * @param targets 目标节点在合并后 PDF 中的全局位置
     * @param options 本次请求的渲染额度
     * @throws IOException 批注读取或外观写入失败时抛出
     * @throws PrintValidationException 超出额度或批注位置无效时抛出
     */
    public void writeMerged(PDDocument pdf, DocumentModel document,
            Map<String, PdfNavigationWriter.GlobalPosition> targets, RenderOptions options) throws IOException {
        writer.writeMerged(pdf, writer.collect(document, options), targets, options);
    }

    /** 创建尚未执行文本框测量的视图，保持原 XHTML 不变。 */
    public static Prepared empty(String xhtml) {
        return new Prepared(new PdfFreeTextLayout.Prepared(xhtml, Map.of()));
    }

    /**
     * 使用默认字体预算测量流式文本框。
     *
     * @param renderer 已完成测量布局的排版器
     * @param xhtml 测量前的 XHTML
     * @param styles 文档命名样式
     * @param options 文档默认字体及缺字回退设置
     * @param fonts 宿主字体目录
     * @return 修正后的 XHTML 和文本框测量快照
     * @throws IOException 字体读取或名称解析失败时抛出
     */
    public static Prepared prepare(PdfBoxRenderer renderer, String xhtml, StyleSheet styles,
            DocumentOptions options, PdfFontCatalog fonts) throws IOException {
        return prepare(renderer, xhtml, styles, options, fonts,
                new PdfFontResources(RenderOptions.DEFAULT_MAX_FONT_CACHE_BYTES));
    }

    /**
     * 测量流式文本框并保存可在关闭测量文档后使用的结果。
     *
     * @param renderer 已完成测量布局的排版器
     * @param xhtml 测量前的 XHTML
     * @param styles 文档命名样式
     * @param options 文档默认字体及缺字回退设置
     * @param fonts 宿主字体目录
     * @param resources 本次渲染共用的字体资源
     * @return 修正后的 XHTML 和文本框测量快照
     * @throws IOException 字体读取或名称解析失败时抛出
     */
    public static Prepared prepare(PdfBoxRenderer renderer, String xhtml, StyleSheet styles,
            DocumentOptions options, PdfFontCatalog fonts, PdfFontResources resources) throws IOException {
        return new Prepared(PdfFreeTextLayout.prepare(renderer, xhtml, styles, options, fonts, resources));
    }

    /**
     * 按默认额度将内部位置占位替换为 FreeText 批注。
     *
     * @param pdf 含内部文本框占位的单元 PDF
     * @param prepared 文本框尺寸、行与字体测量快照
     * @throws IOException 批注读取或外观写入失败时抛出
     * @throws PrintValidationException 超出额度或文本框位置无效时抛出
     */
    public static void writeFreeText(PDDocument pdf, Prepared prepared) throws IOException {
        writeFreeText(pdf, prepared, RenderOptions.defaults());
    }

    /**
     * 按指定额度写入单元内的 FreeText 批注，并使用该额度中的字节预算。
     *
     * @param pdf 含内部文本框占位的单元 PDF
     * @param prepared 文本框尺寸、行与字体测量快照
     * @param options 本次请求的渲染额度
     * @throws IOException 批注读取或外观写入失败时抛出
     * @throws PrintValidationException 超出额度或文本框位置无效时抛出
     */
    public static void writeFreeText(PDDocument pdf, Prepared prepared, RenderOptions options) throws IOException {
        writeFreeText(pdf, prepared, options, new PdfFontResources(options.maxFontCacheBytes()));
    }

    /**
     * 按测量结果写入 FreeText 批注，外观字体对象由当前 PDF 管理。
     *
     * @param pdf 含内部文本框占位的单元 PDF
     * @param prepared 文本框尺寸、行与字体测量快照
     * @param options 本次请求的渲染额度
     * @param resources 本次渲染共用的字体资源
     * @throws IOException 批注读取或外观写入失败时抛出
     * @throws PrintValidationException 超出额度或文本框位置无效时抛出
     */
    public static void writeFreeText(PDDocument pdf, Prepared prepared,
            RenderOptions options, PdfFontResources resources) throws IOException {
        PdfFreeTextWriter.write(pdf, prepared.source.boxes(), options, resources);
    }

    /**
     * 校验整份 PDF 的文本批注物理总数，包含重复区域的副本，排除导航链接。
     *
     * @param pdf 已完成批注写入的合并文档
     * @param options 本次请求的渲染额度
     * @throws IOException 页面批注读取失败时抛出
     * @throws PrintValidationException 最终物理批注总数超额时抛出
     */
    public static void requireAnnotationBudget(PDDocument pdf, RenderOptions options) throws IOException {
        requireAnnotationBudget(pdf, 0, options, "最终合并整份文档物理批注总数");
    }

    /**
     * 按现有文本批注物理数与即将追加的数量检查额度。
     *
     * @param pdf 正在处理的单元或合并 PDF
     * @param additionalCount 当前阶段预计追加的文本批注数量
     * @param options 本次请求的渲染额度
     * @param countScope 超限时报告的计数阶段
     * @throws IOException 页面批注读取失败时抛出
     * @throws PrintValidationException 合计数量超额时抛出
     */
    static void requireAnnotationBudget(PDDocument pdf, long additionalCount,
            RenderOptions options, String countScope) throws IOException {
        long count = additionalCount;
        for (PDPage page : pdf.getPages()) {
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationText || annotation instanceof PDAnnotationFreeText) {
                    count++;
                }
            }
        }
        requireAnnotationBudget(count, options, countScope);
    }

    /**
     * 按当前阶段已确认的数量校验额度。
     *
     * @param count 当前阶段确认的声明数量或物理批注数量
     * @param options 本次请求的渲染额度
     * @param countScope 用于区分声明数、单元下限和整份总数的说明
     * @throws PrintValidationException 已确认数量超额时抛出
     */
    static void requireAnnotationBudget(long count, RenderOptions options, String countScope) {
        if (count > options.maxAnnotations()) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注数量超限：" + countScope + "，实际数量 " + count + "，配置额度 " + options.maxAnnotations());
        }
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
