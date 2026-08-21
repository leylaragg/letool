package io.github.leylaragg.letool.print.autoconfigure;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 从 Spring 自动配置、模板发布到真实 PDF 的纵向验收测试。
 *
 * @author leyland
 */
class PrintStarterVerticalSliceTest {

    /** 加载 Starter 和一条最小业务打印定义。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class))
            .withUserConfiguration(InvoiceConfiguration.class);

    /** 当前版本与明确指定的历史版本都从同一业务门面生成可读取 PDF。 */
    @Test
    void shouldPublishAndRenderCurrentAndHistoricalPdf() {
        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            publisher.publish(1, List.of(document(1, "Historical")));
            publisher.publishAndActivate(2, List.of(document(2, "Current")));

            PrintService service = context.getBean(PrintService.class);
            assertThat(pdfText(service.render("invoice", 42L))).contains("Current 42");
            assertThat(pdfText(service.render(1, "invoice", 43L))).contains("Historical 43");
        });
    }

    /** 显式开启后，SpEL 条件会进入 XML 编译和绑定链路。 */
    @Test
    void shouldUseRestrictedSpelWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("letool.print.spel.enabled=true")
                .run(context -> {
                    TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
                    publisher.publishAndActivate(1, List.of(spelDocument(1)));

                    String text = pdfText(context.getBean(PrintService.class)
                            .render("invoice", 7L));

                    assertThat(text).contains("Approved 7");
                });
    }

    /** 创建包含动态编号的普通 XML 文档模板。 */
    private TemplateDefinition document(long version, String label) {
        return definition(version, "<paragraph>" + label
                + " <field path=\"id\"/></paragraph>");
    }

    /** 创建通过受限 SpEL 判断业务上下文字段的模板。 */
    private TemplateDefinition spelDocument(long version) {
        return definition(version, "<if expression-language=\"spel\" test=\"approved == true\"><then>"
                + "<paragraph>Approved <field path=\"id\"/></paragraph></then></if>");
    }

    /** 把页面正文封装成可发布的文档定义。 */
    private TemplateDefinition definition(long version, String body) {
        String xml = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + body + "</page-body></page></document>";
        PrintTemplate template = new PrintTemplate(
                "invoice-template", TemplateFormat.LETOOL_XML, XmlDsl.VERSION,
                version, 1, xml.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }

    /** 解析真实 PDF，确保验收不只停留在文件头。 */
    private String pdfText(PrintArtifact artifact) {
        assertThat(artifact.content())
                .startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(artifact.content())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new AssertionError("测试生成的 PDF 无法读取", exception);
        }
    }

    /** 提供业务定义，数据查询仍由宿主适配器负责。 */
    @Configuration(proxyBeanMethods = false)
    static class InvoiceConfiguration {

        /** @return 使用 Long 请求创建打印上下文的发票定义 */
        @Bean
        PrintDefinition<Long> invoiceDefinition() {
            return PrintDefinition.of(
                    "invoice", "invoice-template", Long.class,
                    id -> PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                            .put("id", id)
                            .put("approved", true)));
        }
    }
}
