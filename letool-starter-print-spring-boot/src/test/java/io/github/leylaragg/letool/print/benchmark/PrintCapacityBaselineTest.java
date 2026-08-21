package io.github.leylaragg.letool.print.benchmark;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在显式 Maven Profile 下记录三类固定 PDF 输入的本机容量基线。
 *
 * @author leyland
 */
@Tag("print-capacity")
class PrintCapacityBaselineTest {

    /** 每个场景预热一次，随后记录十次完整调用。 */
    private static final int MEASURED_RUNS = 10;

    /** 报告只写入构建目录，不进入版本控制。 */
    private static final Path REPORT = Path.of(
            "target", "print-capacity", "capacity-baseline.md");

    /** 使用真实 Starter 管线执行全部容量场景并写出环境相关报告。 */
    @Test
    void shouldWriteCapacityReportForFixedScenarios() throws IOException {
        assertThat(System.getProperty("letool.print.capacity.enabled"))
                .as("容量测试只能由 print-capacity Profile 显式开启")
                .isEqualTo("true");

        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class))
                .withUserConfiguration(CapacityConfiguration.class);
        List<CapacityResult> results = new ArrayList<>();
        runner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            PrintService service = context.getBean(PrintService.class);
            for (CapacityScenario scenario : scenarios()) {
                publisher.publishAndActivate(scenario.version, List.of(scenario.definition()));
                verifyArtifact(service.render("capacity", scenario.request));
                results.add(measure(service, scenario));
            }
        });

        assertThat(results).hasSize(3);
        writeReport(results);
        assertThat(REPORT).exists();
    }

    /** 执行固定次数并保存稳定产物信息与耗时分位数。 */
    private static CapacityResult measure(PrintService service, CapacityScenario scenario) {
        List<Long> durations = new ArrayList<>(MEASURED_RUNS);
        int pageCount = 0;
        int contentLength = 0;
        int succeeded = 0;
        for (int run = 0; run < MEASURED_RUNS; run++) {
            long startedAt = System.nanoTime();
            PrintArtifact artifact = service.render("capacity", scenario.request);
            durations.add(System.nanoTime() - startedAt);
            pageCount = verifyArtifact(artifact);
            contentLength = artifact.contentLength();
            succeeded++;
        }
        Collections.sort(durations);
        long median = durations.get(durations.size() / 2);
        int p95Index = (int) Math.ceil(durations.size() * 0.95D) - 1;
        return new CapacityResult(
                scenario.name, succeeded, pageCount, contentLength,
                median, durations.get(p95Index));
    }

    /** 重新打开 PDF，并核对渲染器报告的页数。 */
    private static int verifyArtifact(PrintArtifact artifact) {
        assertThat(artifact.content()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(artifact.content())) {
            int pageCount = document.getNumberOfPages();
            assertThat(artifact.metadata()).containsEntry("pageCount", Integer.toString(pageCount));
            return pageCount;
        } catch (IOException exception) {
            throw new AssertionError("容量场景生成的 PDF 无法重新打开", exception);
        }
    }

    /** 创建小型正文、中型表格和大型目录批注文档三个固定场景。 */
    private static List<CapacityScenario> scenarios() {
        return List.of(
                new CapacityScenario("small-text", 1, smallTemplate(), "small"),
                new CapacityScenario("medium-table", 2, tableTemplate(), "table"),
                new CapacityScenario("large-toc-annotations", 3, largeTemplate(), "large"));
    }

    /** 小场景保留最常见的标题、段落和字段绑定。 */
    private static String smallTemplate() {
        return documentBody("<heading id=\"title\" level=\"1\">容量基线</heading>"
                + "<paragraph>请求：<field path=\"title\"/></paragraph>"
                + "<paragraph><field path=\"content\"/></paragraph>");
    }

    /** 中场景让动态行、表头复用和多页排版同时参与测试。 */
    private static String tableTemplate() {
        return documentBody("""
                <heading id="table-title" level="1">表格容量基线</heading>
                <table id="details"><header><row>
                    <cell><paragraph>序号</paragraph></cell>
                    <cell><paragraph>名称</paragraph></cell>
                    <cell><paragraph>说明</paragraph></cell>
                </row></header><body>
                    <for-each items="rows" var="row"><row>
                        <cell><paragraph><field path="$row.index"/></paragraph></cell>
                        <cell><paragraph><field path="$row.name"/></paragraph></cell>
                        <cell><paragraph><field path="$row.description"/></paragraph></cell>
                    </row></for-each>
                </body></table>
                """);
    }

    /** 大场景固定目录、分页和文本便签，覆盖 PDF 分段合并链路。 */
    private static String largeTemplate() {
        StringBuilder body = new StringBuilder("<table-of-contents title=\"目录\"/>");
        for (int chapter = 1; chapter <= 16; chapter++) {
            String id = "chapter-" + chapter;
            body.append("<heading id=\"").append(id).append("\" level=\"1\">第 ")
                    .append(chapter).append(" 章</heading>")
                    .append("<paragraph>").append("固定容量正文 ".repeat(80)).append("</paragraph>");
            if (chapter % 4 == 0) {
                body.append("<annotation type=\"text-note\" target=\"").append(id)
                        .append("\" author=\"reviewer\">复核章节</annotation>");
            }
            if (chapter < 16) {
                body.append("<page-break/>");
            }
        }
        return documentBody(body.toString());
    }

    /** 把场景正文包装成同一 DSL 和上下文版本的文档。 */
    private static String documentBody(String body) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + body + "</page-body></page></document>";
    }

    /** 根据当前 JDK 和机器信息生成可追溯但不参与断言的 Markdown 报告。 */
    private static void writeReport(List<CapacityResult> results) throws IOException {
        Files.createDirectories(REPORT.getParent());
        Runtime runtime = Runtime.getRuntime();
        StringBuilder report = new StringBuilder()
                .append("# Dynamic Print Capacity Baseline\n\n")
                .append("- Generated at: ").append(Instant.now()).append('\n')
                .append("- Java: ").append(System.getProperty("java.version")).append('\n')
                .append("- VM: ").append(System.getProperty("java.vm.name")).append('\n')
                .append("- OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(' ')
                .append(System.getProperty("os.arch")).append('\n')
                .append("- Available processors: ").append(runtime.availableProcessors()).append('\n')
                .append("- Max heap bytes: ").append(runtime.maxMemory()).append("\n\n")
                .append("| Scenario | Success | Pages | Bytes | Median ms | P95 ms |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (CapacityResult result : results) {
            report.append("| ").append(result.name).append(" | ")
                    .append(result.succeeded).append(" | ")
                    .append(result.pageCount).append(" | ")
                    .append(result.contentLength).append(" | ")
                    .append(millis(result.medianNanos)).append(" | ")
                    .append(millis(result.p95Nanos)).append(" |\n");
        }
        Files.writeString(REPORT, report, StandardCharsets.UTF_8);
    }

    /** 把纳秒转为保留三位小数的毫秒文本。 */
    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000D);
    }

    /** 提供容量测试使用的固定业务上下文。 */
    @Configuration(proxyBeanMethods = false)
    static class CapacityConfiguration {

        /** @return 按场景名称生成固定数据规模的测试定义 */
        @Bean
        PrintDefinition<String> capacityDefinition() {
            return PrintDefinition.of(
                    "capacity", "capacity-template", String.class,
                    request -> PrintContext.of(1, context(request)));
        }

        /** 创建指定场景的标准 JSON 数据。 */
        private static ObjectNode context(String request) {
            ObjectNode root = JsonNodeFactory.instance.objectNode()
                    .put("title", request)
                    .put("content", "固定正文 ".repeat(160));
            ArrayNode rows = root.putArray("rows");
            for (int index = 1; index <= 120; index++) {
                rows.addObject()
                        .put("index", index)
                        .put("name", "项目-" + index)
                        .put("description", "固定表格说明 ".repeat(12));
            }
            return root;
        }
    }

    /** 一份固定模板及其业务请求。 */
    private static final class CapacityScenario {

        private final String name;
        private final long version;
        private final String source;
        private final String request;

        /** 创建不可变容量场景。 */
        private CapacityScenario(String name, long version, String source, String request) {
            this.name = name;
            this.version = version;
            this.source = source;
            this.request = request;
        }

        /** @return 可以交给发布器的文档定义 */
        private TemplateDefinition definition() {
            PrintTemplate template = new PrintTemplate(
                    "capacity-template", TemplateFormat.LETOOL_XML, XmlDsl.VERSION,
                    version, 1, source.getBytes(StandardCharsets.UTF_8));
            return new TemplateDefinition(TemplateType.DOCUMENT, template);
        }
    }

    /** 容量报告使用的稳定统计快照。 */
    private static final class CapacityResult {

        private final String name;
        private final int succeeded;
        private final int pageCount;
        private final int contentLength;
        private final long medianNanos;
        private final long p95Nanos;

        /** 保存单个场景的产物和耗时统计。 */
        private CapacityResult(String name, int succeeded, int pageCount, int contentLength,
                               long medianNanos, long p95Nanos) {
            this.name = name;
            this.succeeded = succeeded;
            this.pageCount = pageCount;
            this.contentLength = contentLength;
            this.medianNanos = medianNanos;
            this.p95Nanos = p95Nanos;
        }
    }
}
