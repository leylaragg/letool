package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合并页面序列，并在最终页码稳定后统一写入 PDF 文档级语义。
 *
 * @author leyland
 */
final class PdfDocumentAssembler {

    /** 最终合并阶段写入跨单元导航。 */
    private final PdfNavigationWriter navigationWriter = new PdfNavigationWriter();

    /** 批注需要与正文渲染使用同一份字体目录。 */
    private final PdfAnnotationWriter annotationWriter;

    /** 创建使用指定字体目录的文档组装器。 */
    PdfDocumentAssembler(PdfFontCatalog fontCatalog) {
        this.annotationWriter = new PdfAnnotationWriter(
                Objects.requireNonNull(fontCatalog, "fontCatalog 不能为空"));
    }

    /**
     * 合并稳定轮次的序列文件，并把最终结果交给受控输出。
     *
     * @param document 完整 PDF 文档计划
     * @param results 与页面序列一一对应的排版结果
     * @param sourceTargets 链接源 ID 到目标 ID 的稳定映射
     * @param options 当前渲染治理选项
     * @param workspace 当前请求工作区
     * @param output 调用方受控输出
     * @return 已验证结构的 PDF 结果
     * @throws IOException 合并、保存或复制失败时抛出
     */
    PrintResult assemble(
            PdfDocumentPlan document,
            List<PdfUnitResult> results,
            Map<String, String> sourceTargets,
            RenderOptions options,
            PdfRenderWorkspace workspace,
            PrintOutput output) throws IOException {
        requireCompleteResults(document, results);
        Path finalFile = workspace.allocate();
        int pageCount;
        PDFMergerUtility merger = new PDFMergerUtility();
        try (PDDocument target = new PDDocument()) {
            pageCount = appendAll(target, merger, results, options);
            Map<String, PdfNavigationWriter.GlobalPosition> targets = navigationWriter.write(
                    target, document.document(), results, Map.copyOf(sourceTargets));
            annotationWriter.writeMerged(
                    target, annotationWriter.collect(document.document()), targets);
            applyMetadata(target, document.metadata(), options);
            try (OutputStream temporary = workspace.openOutput(
                    finalFile, options.maxOutputBytes())) {
                target.save(temporary);
            }
        }
        discardResults(results, workspace);
        validate(finalFile, pageCount, options);
        try (InputStream input = Files.newInputStream(finalFile)) {
            input.transferTo(output);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pageCount", Integer.toString(pageCount));
        metadata.put("contentLength", Long.toString(Files.size(finalFile)));
        return output.complete(OutputFormat.PDF, metadata);
    }

    /** 每个页面序列都必须恰好提供一个最终排版结果。 */
    private void requireCompleteResults(
            PdfDocumentPlan document, List<PdfUnitResult> results) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(results, "results 不能为空");
        if (results.size() != document.sequences().size()
                || results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("页面序列排版结果不完整");
        }
    }

    /** 按页面序列顺序追加中间文件，并同步执行最终页数治理。 */
    private int appendAll(
            PDDocument target,
            PDFMergerUtility merger,
            List<PdfUnitResult> results,
            RenderOptions options) throws IOException {
        int pageCount = 0;
        for (PdfUnitResult result : results) {
            try (PDDocument source = Loader.loadPDF(result.file().toFile())) {
                merger.appendDocument(target, source);
            }
            pageCount = Math.addExact(pageCount, result.pageCount());
            if (pageCount > options.maxPages()) {
                throw PrintRenderingException.pageLimitExceeded(options.maxPages());
            }
        }
        return pageCount;
    }

    /** 导航和批注完成后再写入调用方允许公开的元数据。 */
    private void applyMetadata(
            PDDocument pdf, DocumentMetadata metadata, RenderOptions options) {
        PDDocumentInformation information = pdf.getDocumentInformation();
        information.setProducer("letool");
        if (!options.includeDocumentMetadata()) {
            information.setTitle(null);
            information.setAuthor(null);
            return;
        }
        information.setTitle(metadata.title());
        information.setAuthor(metadata.author());
        if (metadata.language() != null) {
            pdf.getDocumentCatalog().setLanguage(metadata.language());
        }
    }

    /** 清理已经合并的中间文件，多个失败都保留在首个异常上。 */
    private void discardResults(
            List<PdfUnitResult> results, PdfRenderWorkspace workspace) throws IOException {
        IOException failure = null;
        for (PdfUnitResult result : results) {
            try {
                workspace.discard(result.file());
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** 重新打开最终文件核对页数，避免把不完整 PDF 交给调用方。 */
    private void validate(Path file, int expectedPages, RenderOptions options) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            int actualPages = pdf.getNumberOfPages();
            if (actualPages != expectedPages || actualPages > options.maxPages()) {
                throw PrintRenderingException.renderFailed(
                        OutputFormat.PDF, new IOException("PDF 最终页数校验失败"));
            }
        }
    }
}
