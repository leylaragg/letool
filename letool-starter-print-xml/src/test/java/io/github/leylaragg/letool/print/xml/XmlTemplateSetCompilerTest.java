package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
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
 * XML 模板集合引用编译测试。
 *
 * @author leyland
 */
class XmlTemplateSetCompilerTest {

    /** 文档可以引用共享片段，集合只公开可打印文档。 */
    @Test
    void shouldCompileDocumentWithNestedFragments() {
        TemplateSet set = set(7,
                definition(TemplateType.DOCUMENT, "main", 7, document(
                        "<include template=\"title\"/><include template=\"body\"/>")),
                definition(TemplateType.FRAGMENT, "title", 7, fragment(
                        "<heading>标题</heading>")),
                definition(TemplateType.FRAGMENT, "body", 7, fragment(
                        "<section><include template=\"title\"/><paragraph>正文</paragraph></section>")));

        CompiledXmlTemplateSet compiled = new XmlTemplateSetCompiler().compile(set);

        assertThat(compiled.templateSetVersion()).isEqualTo(7);
        assertThat(compiled.templateSetDigest()).isEqualTo(set.digest());
        assertThat(compiled.documentCodes()).containsExactly("main");
        assertThat(compiled.require("main").templateCode()).isEqualTo("main");
    }

    /** 单模板入口无法解析集合引用，避免绑定时查询外部仓库。 */
    @Test
    void shouldRejectIncludeFromStandaloneCompiler() {
        PrintTemplate template = template("main", 1, document(
                "<include template=\"shared\"/>"));

        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("模板集合");
    }

    /** 引用目标必须存在且必须是 XML 片段。 */
    @Test
    void shouldRejectMissingOrNonFragmentTarget() {
        XmlTemplateSetCompiler compiler = new XmlTemplateSetCompiler();

        assertThatThrownBy(() -> compiler.compile(set(1,
                definition(TemplateType.DOCUMENT, "main", 1, document(
                        "<include template=\"missing\"/>")))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("missing");

        assertThatThrownBy(() -> compiler.compile(set(1,
                definition(TemplateType.DOCUMENT, "main", 1, document(
                        "<include template=\"other\"/>")),
                definition(TemplateType.DOCUMENT, "other", 1, document(
                        "<paragraph>other</paragraph>")))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("FRAGMENT");
    }

    /** 所有片段都会参与循环检测，即使当前没有文档引用它们。 */
    @Test
    void shouldRejectSelfAndUnreachableCycles() {
        XmlTemplateSetCompiler compiler = new XmlTemplateSetCompiler();

        assertThatThrownBy(() -> compiler.compile(set(1,
                definition(TemplateType.DOCUMENT, "main", 1, document(
                        "<paragraph>ok</paragraph>")),
                definition(TemplateType.FRAGMENT, "a", 1, fragment(
                        "<include template=\"b\"/>")),
                definition(TemplateType.FRAGMENT, "b", 1, fragment(
                        "<include template=\"a\"/>")))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b")
                .hasMessageContaining("循环");
    }

    /** 通过发布器把测试定义整理成受治理的模板集合。 */
    private TemplateSet set(long version, TemplateDefinition... definitions) {
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(version, List.of(definitions));
    }

    /** 将 XML 源包装为指定用途和版本的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code,
                                          long version, String source) {
        return new TemplateDefinition(type, template(code, version, source));
    }

    /** 将 XML 源保存为测试使用的模板快照。 */
    private PrintTemplate template(String code, long version, String source) {
        return new PrintTemplate(code, TemplateFormat.LETOOL_XML, 1, version, 1,
                source.getBytes(StandardCharsets.UTF_8));
    }

    /** 把块级 XML 放入最小完整文档。 */
    private String document(String blocks) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + blocks + "</page-body></page></document>";
    }

    /** 把块级 XML 放入共享片段。 */
    private String fragment(String blocks) {
        return "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">"
                + blocks + "</fragment>";
    }
}
