package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentModel;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * 汇总 PDF 测试常用的流式结果和内存内容。
 *
 * @author leyland
 */
final class RenderedPdf {

    /** 渲染器返回的无正文结果。 */
    private final PrintResult result;

    /** 调用方输出流收到的 PDF 内容。 */
    private final byte[] content;

    /** 保存一次测试渲染的结果和内容。 */
    private RenderedPdf(PrintResult result, byte[] content) {
        this.result = result;
        this.content = Arrays.copyOf(content, content.length);
    }

    /**
     * 使用与选项一致的容量限制执行一次内存渲染。
     *
     * @param renderer 待测 PDF 渲染器
     * @param document 测试文档
     * @param options 渲染选项
     * @return 可分别断言结果和 PDF 内容的测试对象
     */
    static RenderedPdf render(
            OpenHtmlPdfRenderer renderer,
            DocumentModel document,
            RenderOptions options) {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        PrintOutput output = new PrintOutput(target, options.maxOutputBytes());
        PrintResult result = renderer.render(document, options, output);
        byte[] content = target.toByteArray();
        if (result.contentLength() != content.length) {
            throw new AssertionError("流式结果长度与 PDF 内容不一致");
        }
        return new RenderedPdf(result, content);
    }

    /** @return 渲染器返回的流式结果 */
    PrintResult result() {
        return result;
    }

    /** @return PDF 内容的独立副本 */
    byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
