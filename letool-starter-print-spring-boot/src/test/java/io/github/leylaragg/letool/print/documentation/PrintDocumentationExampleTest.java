package io.github.leylaragg.letool.print.documentation;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.autoconfigure.PrintAutoConfiguration;
import io.github.leylaragg.letool.print.autoconfigure.PrintSpelAutoConfiguration;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 让模板作者指南里的完整示例持续经过真实发布和 PDF 输出链路。
 *
 * @author leyland
 */
class PrintDocumentationExampleTest {

    /** Markdown 中 XML 代码块的稳定提取规则。 */
    private static final Pattern XML_BLOCK = Pattern.compile("```xml\\R(.*?)\\R```", Pattern.DOTALL);

    /** 文档改动后，示例仍应能原样发布并生成可读取的 PDF。 */
    @Test
    void shouldRenderTemplateAuthorGuideExample() throws IOException {
        List<String> examples = xmlExamples();
        assertThat(examples).hasSizeGreaterThanOrEqualTo(2);

        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class))
                .withUserConfiguration(DocumentationConfiguration.class);
        runner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            publisher.publishAndActivate(1, List.of(
                    definition("invoice-template", TemplateType.DOCUMENT, examples.get(0)),
                    definition("invoice-items", TemplateType.FRAGMENT, examples.get(1))));

            PrintArtifact artifact = context.getBean(PrintService.class).render("invoice-guide", 1L);
            assertThat(artifact.content()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
            try (PDDocument document = Loader.loadPDF(artifact.content())) {
                assertThat(document.getNumberOfPages()).isPositive();
                assertThat(document.getPage(0).getAnnotations()).isNotEmpty();
            }
        });
    }

    /** 从仓库根目录或模块目录找到指南，兼容 Maven 与 IDE 的工作目录。 */
    private static List<String> xmlExamples() throws IOException {
        Path guide = Path.of("docs", "dynamic-print-template-author-guide.md");
        if (!Files.exists(guide)) {
            guide = Path.of("..", "docs", "dynamic-print-template-author-guide.md").normalize();
        }
        String markdown = Files.readString(guide, StandardCharsets.UTF_8);
        Matcher matcher = XML_BLOCK.matcher(markdown);
        List<String> examples = new ArrayList<>();
        while (matcher.find()) {
            examples.add(matcher.group(1));
        }
        return examples;
    }

    /** 把指南中的源码包装成同一模板集合版本。 */
    private static TemplateDefinition definition(String code, TemplateType type, String source) {
        PrintTemplate template = new PrintTemplate(
                code, TemplateFormat.LETOOL_XML, XmlDsl.VERSION, 1, 1,
                source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }

    /** 提供与指南 JSON 片段一致的业务上下文。 */
    @Configuration(proxyBeanMethods = false)
    static class DocumentationConfiguration {

        /** @return 指南示例使用的打印定义 */
        @Bean
        PrintDefinition<Long> invoiceGuideDefinition() {
            return PrintDefinition.of(
                    "invoice-guide", "invoice-template", Long.class,
                    ignored -> PrintContext.of(1, invoiceContext()));
        }

        /** 创建发票、明细和格式化字段。 */
        private static ObjectNode invoiceContext() {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            ObjectNode invoice = root.putObject("invoice")
                    .put("no", "INV-2026-001")
                    .put("customer", "示例客户")
                    .put("paid", true)
                    .put("total", 1280.50D);
            ArrayNode items = invoice.putArray("items");
            items.addObject().put("name", "项目 A").put("amount", 800);
            items.addObject().put("name", "项目 B").put("amount", 480.50D);
            return root;
        }
    }
}
