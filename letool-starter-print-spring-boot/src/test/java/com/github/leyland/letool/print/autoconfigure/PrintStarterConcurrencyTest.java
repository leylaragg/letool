package com.github.leyland.letool.print.autoconfigure;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.leyland.letool.print.api.PrintArtifact;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.service.PrintDefinition;
import com.github.leyland.letool.print.service.PrintService;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateRepository;
import com.github.leyland.letool.print.template.TemplateSetPublisher;
import com.github.leyland.letool.print.template.TemplateType;
import com.github.leyland.letool.print.xml.XmlDsl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板热切换期间的 Starter 并发快照测试。
 *
 * @author leyland
 */
class PrintStarterConcurrencyTest {

    /** 每次请求只能看到一个完整模板版本和自己的上下文。 */
    @Test
    void shouldNotMixVersionsOrContextsWhileActivatingTemplates() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class))
                .withUserConfiguration(ConcurrentPrintConfiguration.class);

        runner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            publisher.publishAndActivate(1, List.of(document(1, "VersionOne")));
            publisher.publish(2, List.of(document(2, "VersionTwo")));

            PrintService service = context.getBean(PrintService.class);
            TemplateRepository repository = context.getBean(TemplateRepository.class);
            ExecutorService executor = Executors.newFixedThreadPool(6);
            try {
                List<Callable<String>> work = new ArrayList<>();
                for (long id = 1; id <= 20; id++) {
                    long requestId = id;
                    work.add(() -> pdfText(service.render("concurrent", requestId)));
                    work.add(() -> {
                        repository.activate(requestId % 2 == 0 ? 1 : 2);
                        return "switched";
                    });
                }

                List<Future<String>> futures = executor.invokeAll(work);
                for (int index = 0; index < futures.size(); index += 2) {
                    long requestId = index / 2L + 1;
                    String text = futures.get(index).get();
                    assertThat(text).contains(Long.toString(requestId));
                    assertThat(text.contains("VersionOne") ^ text.contains("VersionTwo")).isTrue();
                }
            } finally {
                executor.shutdownNow();
            }
        });
    }

    /** 创建正文可区分的模板版本。 */
    private TemplateDefinition document(long version, String label) {
        String xml = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><paragraph>" + label
                + " <field path=\"id\"/></paragraph></page></document>";
        PrintTemplate template = new PrintTemplate(
                "concurrent-template", TemplateFormat.LETOOL_XML, XmlDsl.VERSION,
                version, 1, xml.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }

    /** 提取每个并发任务生成的 PDF 正文。 */
    private static String pdfText(PrintArtifact artifact) {
        try (PDDocument document = Loader.loadPDF(artifact.content())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new AssertionError("并发测试生成了无法读取的 PDF", exception);
        }
    }

    /** 声明并发测试使用的无状态数据适配器。 */
    @Configuration(proxyBeanMethods = false)
    static class ConcurrentPrintConfiguration {

        /** @return 把每个请求编号放入独立上下文的打印定义 */
        @Bean
        PrintDefinition<Long> concurrentDefinition() {
            return PrintDefinition.of(
                    "concurrent", "concurrent-template", Long.class,
                    id -> PrintContext.of(1,
                            JsonNodeFactory.instance.objectNode().put("id", id)));
        }
    }
}
