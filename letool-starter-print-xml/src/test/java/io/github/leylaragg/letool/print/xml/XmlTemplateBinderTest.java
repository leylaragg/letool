package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageSize;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 编译快照绑定通用文档模型的契约测试。
 *
 * @author leyland
 */
class XmlTemplateBinderTest {

    /** 验证完整静态基础模板能够绑定为跨格式文档模型。 */
    @Test
    void shouldBindStaticTemplateToDocumentModel() {
        CompiledXmlTemplate compiled = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1"
                          title="保险合同"
                          author="Letool"
                          language="zh-CN">
                    <page size="LETTER" orientation="landscape" margin="18mm"><page-body>
                        <section id="summary">
                            <heading id="title" level="2">合同摘要</heading>
                            <paragraph id="intro">投保人：<text>张三</text></paragraph>
                            <page-break/>
                        </section>
                    </page-body></page>
                </document>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(compiled, context(1, "first"));

        assertThat(model.metadata().title()).isEqualTo("保险合同");
        assertThat(model.metadata().author()).isEqualTo("Letool");
        assertThat(model.metadata().language()).isEqualTo("zh-CN");
        assertThat(model.pageSequences()).hasSize(1);
        assertThat(model.styleSheet().hasNamedStyles()).isFalse();
        assertThat(XmlTestDocuments.sequence(model).pageLayout().pageSize())
                .isEqualTo(PageSize.LETTER);
        assertThat(XmlTestDocuments.sequence(model).pageLayout().orientation())
                .isEqualTo(PageOrientation.LANDSCAPE);
        assertThat(XmlTestDocuments.sequence(model).pageLayout().margins())
                .isEqualTo(new Margins(18_000, 18_000, 18_000, 18_000));
        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new SectionNode("summary", List.of(
                        new HeadingNode("title", 2, List.of(new TextNode("合同摘要"))),
                        new ParagraphNode("intro", List.of(
                                new TextNode("投保人："),
                                new TextNode("张三"))),
                        PageBreakNode.INSTANCE)));
    }

    /** 验证同一编译快照可以安全绑定不同但同版本的上下文。 */
    @Test
    void shouldReuseCompiledTemplateAcrossMatchingContexts() {
        CompiledXmlTemplate compiled = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body><paragraph>固定正文</paragraph></page-body></page>
                </document>
                """);

        DocumentModel first = new XmlTemplateBinder().bind(compiled, context(1, "first"));
        DocumentModel second = new XmlTemplateBinder().bind(compiled, context(1, "second"));

        assertThat(DocumentTraversal.depthFirst(first))
                .containsExactlyElementsOf(DocumentTraversal.depthFirst(second));
    }

    /** 验证绑定阶段再次校验上下文契约版本。 */
    @Test
    void shouldRejectMismatchedContextVersion() {
        CompiledXmlTemplate compiled = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body><paragraph>固定正文</paragraph></page-body></page>
                </document>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(compiled, context(2, "other")))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("PRINT_001");
    }

    /** 编译测试模板。 */
    private CompiledXmlTemplate compile(String xml) {
        PrintTemplate template = new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 7, 1,
                xml.getBytes(StandardCharsets.UTF_8));
        return new XmlTemplateCompiler().compile(template);
    }

    /** 创建带区分值的只读上下文。 */
    private PrintContext context(int version, String value) {
        return PrintContext.of(
                version,
                JsonNodeFactory.instance.objectNode().put("value", value));
    }
}
