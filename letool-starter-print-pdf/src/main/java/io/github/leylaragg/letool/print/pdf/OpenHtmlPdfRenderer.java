package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.pdf.internal.render.PdfRenderEngine;
import io.github.leylaragg.letool.print.render.OutputCapability;

import java.nio.file.Path;

/**
 * 使用受控 XHTML、OpenHTMLToPDF 和 PDFBox 生成 PDF 的稳定公开入口。
 *
 * <p>构造方式和渲染契约保持不变；内部引擎负责独立请求的排版状态与临时工作区。</p>
 *
 * @author leyland
 */
public final class OpenHtmlPdfRenderer implements PdfRenderer {

    /** 仅持有不可变配置，每次渲染状态由引擎在调用内创建。 */
    private final PdfRenderEngine engine;

    /**
     * 使用系统临时目录创建渲染器。
     *
     * @param fontCatalog 宿主拥有并授权使用的字体目录
     */
    public OpenHtmlPdfRenderer(PdfFontCatalog fontCatalog) {
        this(fontCatalog, Path.of(System.getProperty("java.io.tmpdir"), "letool", "print-pdf"));
    }

    /**
     * 使用宿主指定的临时根目录创建渲染器。
     *
     * @param fontCatalog 宿主字体目录
     * @param temporaryRoot 可信且可写的临时根目录
     */
    public OpenHtmlPdfRenderer(PdfFontCatalog fontCatalog, Path temporaryRoot) {
        this(fontCatalog, temporaryRoot, null);
    }

    /**
     * 创建支持可选最终后处理的渲染器。
     *
     * @param fontCatalog 宿主字体目录
     * @param temporaryRoot 可信临时根目录
     * @param postProcessor 网络阅读优化等最终处理器；null 表示未注册
     * @throws NullPointerException 字体目录或临时根目录为空
     */
    public OpenHtmlPdfRenderer(PdfFontCatalog fontCatalog, Path temporaryRoot, PdfPostProcessor postProcessor) {
        this.engine = new PdfRenderEngine(fontCatalog, temporaryRoot, postProcessor);
    }

    /** @return PDF 输出格式 */
    @Override
    public OutputFormat outputFormat() {
        return engine.outputFormat();
    }

    /** @return 引擎支持的公共文档能力 */
    @Override
    public OutputCapability capability() {
        return engine.capability();
    }

    /**
     * 使用同一内部链路完成布局、分页、组装和受控输出。
     *
     * @param document 不可变文档模型
     * @param options 渲染容量与页数限制
     * @param output 调用方的受控输出
     * @return 最终渲染结果
     */
    @Override
    public PrintResult render(DocumentModel document, RenderOptions options, PrintOutput output) {
        return engine.render(document, options, output);
    }
}
