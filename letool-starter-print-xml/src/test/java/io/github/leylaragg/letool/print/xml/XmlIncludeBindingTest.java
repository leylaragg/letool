package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 片段闭合作用域绑定测试。
 *
 * @author leyland
 */
class XmlIncludeBindingTest {

    /** JSON 测试数据解析器。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 片段可以读取根数据并使用自己声明的循环变量。 */
    @Test
    void shouldBindRootDataAndFragmentLocalLoop() throws Exception {
        CompiledXmlTemplate template = compile(
                definition(TemplateType.DOCUMENT, "main", document(
                        "<include template=\"content\"/>")),
                definition(TemplateType.FRAGMENT, "content", fragment("""
                        <heading><field path="title"/></heading>
                        <for-each items="items" var="item">
                            <paragraph><field path="$item.name"/></paragraph>
                        </for-each>
                        """)));

        DocumentModel model = new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JSON.readTree(
                        "{\"title\":\"清单\",\"items\":[{\"name\":\"A\"},{\"name\":\"B\"}]}")));

        assertThat(model.blocks()).containsExactly(
                new HeadingNode("", 1, List.of(new TextNode("清单"))),
                new ParagraphNode("", List.of(new TextNode("A"))),
                new ParagraphNode("", List.of(new TextNode("B"))));
    }

    /** 引用点外层循环变量不会进入片段词法作用域。 */
    @Test
    void shouldRejectCallerLoopVariableCapture() {
        TemplateSet set = set(
                definition(TemplateType.DOCUMENT, "main", document("""
                        <for-each items="items" var="item">
                            <include template="row"/>
                        </for-each>
                        """)),
                definition(TemplateType.FRAGMENT, "row", fragment(
                        "<paragraph><field path=\"$item.name\"/></paragraph>")));

        assertThatThrownBy(() -> new XmlTemplateSetCompiler().compile(set))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("未声明变量");
    }

    /** 片段不会改写 ID，多次引用仍由文档模型执行全局唯一性校验。 */
    @Test
    void shouldRejectDuplicateIdsFromRepeatedFragment() {
        CompiledXmlTemplate template = compile(
                definition(TemplateType.DOCUMENT, "main", document(
                        "<include template=\"part\"/><include template=\"part\"/>")),
                definition(TemplateType.FRAGMENT, "part", fragment(
                        "<paragraph id=\"fixed\">x</paragraph>")));

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JSON.createObjectNode())))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("fixed")
                .hasMessageContaining("重复");
    }

    /** 将一组模板定义编译成主文档。 */
    private CompiledXmlTemplate compile(TemplateDefinition... definitions) {
        return new XmlTemplateSetCompiler().compile(set(definitions)).require("main");
    }

    /** 通过发布器把测试定义整理成受治理的模板集合。 */
    private TemplateSet set(TemplateDefinition... definitions) {
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(definitions));
    }

    /** 将 XML 源包装为指定用途的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        PrintTemplate template = new PrintTemplate(code, TemplateFormat.LETOOL_XML,
                1, 1, 1, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }

    /** 把块级 XML 放入最小完整文档。 */
    private String document(String blocks) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page>" + blocks + "</page></document>";
    }

    /** 把块级 XML 放入共享片段。 */
    private String fragment(String blocks) {
        return "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">"
                + blocks + "</fragment>";
    }
}
