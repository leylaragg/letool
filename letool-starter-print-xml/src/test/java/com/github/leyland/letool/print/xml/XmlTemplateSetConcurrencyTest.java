package com.github.leyland.letool.print.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.template.InMemoryTemplateRepository;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateSet;
import com.github.leyland.letool.print.template.TemplateSetPublisher;
import com.github.leyland.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML 模板集合编译与绑定并发复用测试。
 *
 * @author leyland
 */
class XmlTemplateSetConcurrencyTest {

    /** 同一无状态编译器和同一集合快照可被多个请求并发复用。 */
    @Test
    void shouldCompileAndBindConcurrently() throws Exception {
        TemplateSet source = source();
        XmlTemplateSetCompiler compiler = new XmlTemplateSetCompiler();
        CompiledXmlTemplate template = compiler.compile(source).require("main");
        ObjectMapper json = new ObjectMapper();
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                int value = index;
                tasks.add(() -> {
                    CompiledXmlTemplate concurrent = compiler.compile(source).require("main");
                    ParagraphNode paragraph = (ParagraphNode) new XmlTemplateBinder().bind(
                            value % 2 == 0 ? template : concurrent,
                            PrintContext.of(1, json.readTree("{\"name\":\"N" + value + "\"}")))
                            .blocks().get(0);
                    return ((TextNode) paragraph.children().get(0)).text();
                });
            }

            assertThat(executor.invokeAll(tasks)).allSatisfy(
                    future -> assertThat(future.get()).startsWith("N"));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 准备一个包含字段片段、可被并发复用的模板集合。 */
    private TemplateSet source() {
        String document = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><include template=\"field\"/>"
                + "</page></document>";
        String fragment = "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\"><paragraph><field path=\"name\"/></paragraph></fragment>";
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(
                        definition(TemplateType.DOCUMENT, "main", document),
                        definition(TemplateType.FRAGMENT, "field", fragment)));
    }

    /** 将 XML 源转换为测试使用的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        PrintTemplate template = new PrintTemplate(code, TemplateFormat.LETOOL_XML,
                1, 1, 1, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}
