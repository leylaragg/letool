package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pdf.PdfDocumentRenderer;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 模板从仓库快照到最终打印产物的管线测试。
 *
 * @author leyland
 */
class XmlPrintPipelineTest {

    /** 非 Spring 使用方手动组合组件后也能生成真实 PDF。 */
    @Test
    void shouldCompileBindAndRenderLockedTemplate() {
        TemplateRepository repository = new InMemoryTemplateRepository();
        PrintTemplate template = publish(repository, 1, "Hello XML pipeline");
        XmlPrintPipeline pipeline = pipeline(repository, 7);

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        PrintOutput output = new PrintOutput(target, RenderOptions.DEFAULT_MAX_OUTPUT_BYTES);
        PrintResult result = pipeline.render(request(template), output);

        assertThat(pipeline.templateFormat()).isEqualTo(TemplateFormat.LETOOL_XML);
        assertThat(pipeline.supportedOutputs()).containsExactly(OutputFormat.PDF);
        assertThat(result.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(target.toByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(result.metadata()).containsKeys("pageCount", "contentLength");
    }

    /** 已经锁定的请求不能在同版本仓库之外替换模板正文。 */
    @Test
    void shouldRejectRequestTemplateOutsideRepositorySnapshot() {
        TemplateRepository repository = new InMemoryTemplateRepository();
        PrintTemplate stored = publish(repository, 1, "stored");
        PrintTemplate changed = new PrintTemplate(
                stored.templateCode(), stored.templateFormat(), stored.dslVersion(),
                stored.templateSetVersion(), stored.contextVersion(), xml("secret changed"));
        XmlPrintPipeline pipeline = pipeline(repository, 1);

        assertThatThrownBy(() -> pipeline.render(request(changed), output()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板快照不一致")
                .hasMessageNotContaining("secret changed");
    }

    /** 管线在读取仓库前先拒绝不属于自身的模板格式。 */
    @Test
    void shouldRejectMismatchedTemplateFormat() {
        TemplateRepository repository = new InMemoryTemplateRepository();
        PrintTemplate other = new PrintTemplate(
                "main", new TemplateFormat("other-template"), 1, 1, 1,
                "other".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> pipeline(repository, 1).render(request(other), output()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板格式");
    }

    /** 组合 XML 编译、绑定和 PDF 渲染依赖。 */
    private XmlPrintPipeline pipeline(TemplateRepository repository, long profileVersion) {
        XmlTemplateCompiler compiler = new XmlTemplateCompiler();
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(
                new XmlTemplateSetCompiler(compiler));
        return new XmlPrintPipeline(
                new XmlTemplateCompilationService(repository, cache),
                new XmlTemplateBinder(),
                new DocumentRendererRegistry(List.of(new PdfDocumentRenderer(List.of()))),
                profileVersion);
    }

    /** 发布并激活单文档模板。 */
    private PrintTemplate publish(TemplateRepository repository, long version, String text) {
        PrintTemplate template = new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, XmlDsl.VERSION, version, 1, xml(text));
        new TemplateSetPublisher(repository, List.of()).publishAndActivate(
                version, List.of(new TemplateDefinition(TemplateType.DOCUMENT, template)));
        return template;
    }

    /** 创建版本匹配的同步 PDF 请求。 */
    private PrintRequest request(PrintTemplate template) {
        return new PrintRequest(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode()),
                OutputFormat.PDF,
                Locale.CHINA,
                ZoneId.of("Asia/Shanghai"),
                RenderOptions.defaults());
    }

    /** 创建测试使用的受控内存输出。 */
    private PrintOutput output() {
        return new PrintOutput(new ByteArrayOutputStream(), RenderOptions.DEFAULT_MAX_OUTPUT_BYTES);
    }

    /** 将测试正文包进最小可打印 XML。 */
    private byte[] xml(String text) {
        String source = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><paragraph>" + text
                + "</paragraph></page></document>";
        return source.getBytes(StandardCharsets.UTF_8);
    }
}
