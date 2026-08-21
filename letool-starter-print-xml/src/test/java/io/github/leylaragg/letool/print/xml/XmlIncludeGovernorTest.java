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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 片段引用的编译期容量治理测试。
 *
 * @author leyland
 */
class XmlIncludeGovernorTest {

    /** 各项累计值恰好等于上限时仍应允许编译。 */
    @Test
    void shouldAllowExactLimits() {
        XmlTemplateSetCompiler compiler = compiler(6, 5, 1, 4);

        assertThatCode(() -> compiler.compile(set(
                document("<include template=\"part\"/>"),
                fragment("part", "<paragraph>x</paragraph>"))))
                .doesNotThrowAnyException();
    }

    /** 集合原始节点总数按每份 XML 源累计。 */
    @Test
    void shouldLimitRawTemplateSetNodes() {
        XmlTemplateSetCompiler compiler = compiler(4, 100, 10, 64);

        assertThatThrownBy(() -> compiler.compile(set(
                document("<include template=\"part\"/>"),
                fragment("part", "<paragraph>x</paragraph>"))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("集合节点数量");
    }

    /** 同一共享片段重复出现时必须逐次计入展开节点。 */
    @Test
    void shouldCountExpandedFragmentPerOccurrence() {
        XmlTemplateSetCompiler compiler = compiler(100, 5, 10, 64);

        assertThatThrownBy(() -> compiler.compile(set(
                document("<include template=\"part\"/><include template=\"part\"/>"),
                fragment("part", "<paragraph>x</paragraph>"))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("展开节点数量");
    }

    /** 引用链和最终结构深度分别治理。 */
    @Test
    void shouldLimitIncludeChainAndExpandedDepth() {
        assertThatThrownBy(() -> compiler(100, 100, 2, 64).compile(set(
                document("<include template=\"a\"/>"),
                fragment("a", "<include template=\"b\"/>"),
                fragment("b", "<include template=\"c\"/>"),
                fragment("c", "<paragraph>x</paragraph>"))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("引用深度");

        assertThatThrownBy(() -> compiler(100, 100, 10, 4).compile(set(
                document("<include template=\"a\"/>"),
                fragment("a", "<section><include template=\"b\"/></section>"),
                fragment("b", "<section><paragraph>x</paragraph></section>"))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("结构深度");
    }

    /** 使用较小阈值组装集合编译器，方便覆盖容量边界。 */
    private XmlTemplateSetCompiler compiler(int rawNodes, int expandedNodes,
                                            int includeDepth, int structureDepth) {
        XmlTemplateSetGovernor governor =
                new XmlTemplateSetGovernor(rawNodes, expandedNodes, includeDepth, structureDepth);
        return new XmlTemplateSetCompiler(new XmlTemplateCompiler(), governor);
    }

    /** 通过发布器把测试定义整理成受治理的模板集合。 */
    private TemplateSet set(TemplateDefinition... definitions) {
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(definitions));
    }

    /** 把块级 XML 包装成主文档定义。 */
    private TemplateDefinition document(String blocks) {
        String source = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + blocks + "</page-body></page></document>";
        return definition(TemplateType.DOCUMENT, "main", source);
    }

    /** 把块级 XML 包装成指定代码的片段定义。 */
    private TemplateDefinition fragment(String code, String blocks) {
        return definition(TemplateType.FRAGMENT, code,
                "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">"
                        + blocks + "</fragment>");
    }

    /** 将 XML 源转换为测试使用的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        PrintTemplate template = new PrintTemplate(code, TemplateFormat.LETOOL_XML,
                1, 1, 1, source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }
}
