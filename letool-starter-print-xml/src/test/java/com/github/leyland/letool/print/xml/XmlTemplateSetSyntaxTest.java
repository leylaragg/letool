package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.template.InMemoryTemplateRepository;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateSet;
import com.github.leyland.letool.print.template.TemplateSetPublisher;
import com.github.leyland.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * fragment 和 include 的受控 XML 语法测试。
 *
 * @author leyland
 */
class XmlTemplateSetSyntaxTest {

    /** include 只允许出现在块级容器，并且自身必须为空。 */
    @Test
    void shouldRejectInlineAndNonEmptyInclude() {
        assertThatThrownBy(() -> compile(
                document("<paragraph><include template=\"part\"/></paragraph>"),
                fragment("part", "<paragraph>x</paragraph>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("paragraph 不能包含 include");

        assertThatThrownBy(() -> compile(
                document("<include template=\"part\"><paragraph>x</paragraph></include>"),
                fragment("part", "<paragraph>x</paragraph>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("include 不能包含 paragraph");
    }

    /** 表格行动态域不能通过 include 间接生成普通块。 */
    @Test
    void shouldRejectIncludeFromTableRowDomain() {
        assertThatThrownBy(() -> compile(
                document("""
                        <table><body>
                            <if path="enabled" operator="truthy">
                                <include template="part"/>
                            </if>
                        </body></table>
                        """),
                fragment("part", "<paragraph>x</paragraph>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("只能产生 row");
    }

    /** 其他格式文档可以共存，但不能成为 XML include 目标。 */
    @Test
    void shouldIgnoreOtherDocumentsAndRejectOtherFormatFragment() {
        TemplateDefinition jasperDocument = definition(
                TemplateType.DOCUMENT, "jasper", TemplateFormat.JASPER_JRXML,
                1, 1, "<jasperReport/>");
        CompiledXmlTemplateSet compiled = compile(
                document("<paragraph>xml</paragraph>"), jasperDocument);
        assertThat(compiled.documentCodes()).containsExactly("main");

        TemplateDefinition jasperFragment = definition(
                TemplateType.FRAGMENT, "part", TemplateFormat.JASPER_JRXML,
                1, 1, "<component/>");
        assertThatThrownBy(() -> compile(
                document("<include template=\"part\"/>"), jasperFragment))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("letool-xml");
    }

    /** 引用两端必须使用相同 DSL 和上下文版本。 */
    @Test
    void shouldRejectVersionMismatch() {
        TemplateDefinition main = document("<include template=\"part\"/>");
        TemplateDefinition contextMismatch = definition(
                TemplateType.FRAGMENT, "part", TemplateFormat.LETOOL_XML,
                1, 2, fragmentSource("<paragraph>x</paragraph>"));

        assertThatThrownBy(() -> compile(main, contextMismatch))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("版本不一致");
    }

    /** 将测试定义发布并编译成 XML 模板集合。 */
    private CompiledXmlTemplateSet compile(TemplateDefinition... definitions) {
        TemplateSet set = new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(definitions));
        return new XmlTemplateSetCompiler().compile(set);
    }

    /** 把块级 XML 包装成主文档定义。 */
    private TemplateDefinition document(String blocks) {
        return definition(TemplateType.DOCUMENT, "main", TemplateFormat.LETOOL_XML,
                1, 1, "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                        + "\" context-version=\"1\"><page>" + blocks
                        + "</page></document>");
    }

    /** 把块级 XML 包装成指定代码的片段定义。 */
    private TemplateDefinition fragment(String code, String blocks) {
        return definition(TemplateType.FRAGMENT, code, TemplateFormat.LETOOL_XML,
                1, 1, fragmentSource(blocks));
    }

    /** 把块级 XML 放入共享片段根标签。 */
    private String fragmentSource(String blocks) {
        return "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">"
                + blocks + "</fragment>";
    }

    /** 将源码包装成带有指定格式和版本的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, TemplateFormat format,
                                          int dslVersion, int contextVersion, String source) {
        PrintTemplate template = new PrintTemplate(code, format, dslVersion,
                1, contextVersion, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}
