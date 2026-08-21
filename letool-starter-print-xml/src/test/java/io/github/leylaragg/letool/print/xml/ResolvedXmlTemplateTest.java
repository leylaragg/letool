package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.template.TemplateCompilationKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 已解析 XML 模板快照测试。
 *
 * @author leyland
 */
class ResolvedXmlTemplateTest {

    /** 快照应把编译条件与不透明编译结果一起交给后续渲染管线。 */
    @Test
    void shouldKeepCompilationKeyAndCompiledTemplate() {
        PrintTemplate source = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                document("<paragraph>正文</paragraph>").getBytes(StandardCharsets.UTF_8));
        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(source);
        TemplateCompilationKey key = new TemplateCompilationKey(7, "a".repeat(64), "main",
                1, 1, 3, OutputFormat.PDF);

        ResolvedXmlTemplate resolved = new ResolvedXmlTemplate(key, compiled);

        assertThat(resolved.key()).isSameAs(key);
        assertThat(resolved.template()).isSameAs(compiled);
        assertThat(resolved.inspection()).isSameAs(compiled.inspection());
        assertThat(resolved.toString()).contains("main").doesNotContain("secret-template-text");
    }

    /** inspection 是编译快照的一部分，并发读取不应产生额外副本或状态漂移。 */
    @Test
    void shouldShareInspectionAcrossConcurrentReaders() throws Exception {
        PrintTemplate source = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                document("<paragraph><field path=\"customer.name\"/></paragraph>")
                        .getBytes(StandardCharsets.UTF_8));
        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(source);
        TemplateCompilationKey key = new TemplateCompilationKey(7, "a".repeat(64), "main",
                1, 1, 3, OutputFormat.PDF);
        ResolvedXmlTemplate resolved = new ResolvedXmlTemplate(key, compiled);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            var readers = IntStream.range(0, 32)
                    .<Callable<Object>>mapToObj(index -> resolved::inspection)
                    .toList();

            for (var result : executor.invokeAll(readers)) {
                assertThat(result.get()).isSameAs(compiled.inspection());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** 不完整的解析快照应在 Java API 边界直接失败。 */
    @Test
    void shouldRejectNullSnapshotParts() {
        PrintTemplate source = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                document("<paragraph>正文</paragraph>").getBytes(StandardCharsets.UTF_8));
        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(source);
        TemplateCompilationKey key = new TemplateCompilationKey(7, "a".repeat(64), "main",
                1, 1, 3, OutputFormat.PDF);

        assertThatNullPointerException().isThrownBy(() -> new ResolvedXmlTemplate(null, compiled));
        assertThatNullPointerException().isThrownBy(() -> new ResolvedXmlTemplate(key, null));
    }

    /** 生成最小完整 XML 文档。 */
    private String document(String blocks) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + blocks + "</page-body></page></document>";
    }
}
