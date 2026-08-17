package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 安全 XML 模板编译器的公开契约测试。
 *
 * @author leyland
 */
class XmlTemplateCompilerTest {

    /** 验证编译入口对空模板给出稳定参数说明。 */
    @Test
    void shouldRejectNullTemplate() {
        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("template 不能为空");
    }

    /** 验证最小受控 XML 可以编译为不透明的不可变快照。 */
    @Test
    void shouldCompileMinimalTemplate() {
        PrintTemplate template = template("""
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1">
                    <page>
                        <paragraph>合同正文</paragraph>
                    </page>
                </document>
                """);

        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(template);

        assertThat(compiled.templateCode()).isEqualTo("contract");
        assertThat(compiled.dslVersion()).isEqualTo(1);
        assertThat(compiled.contextVersion()).isEqualTo(1);
        assertThat(compiled.templateSetVersion()).isEqualTo(7);
    }

    /** 验证 XML 声明、实体和外部资源入口在解析前被拒绝。 */
    @Test
    void shouldRejectExternalResourceAndEntityFeatures() {
        List<String> unsafeSources = List.of(
                "<!DOCTYPE document [<!ENTITY value 'secret'>]>" + minimal("&value;"),
                "<!DOCTYPE document [<!ENTITY xxe SYSTEM 'file:///forbidden'>]>" + minimal("&xxe;"),
                """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          xmlns:xi="http://www.w3.org/2001/XInclude"
                          context-version="1">
                    <page><xi:include href="file:///forbidden"/></page>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="https://leyland.github.io/letool/print/v1 file:///forbidden"
                          context-version="1">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """,
                """
                <?unsafe execute?>
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """);

        for (String source : unsafeSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列")
                    .hasMessageNotContaining("file:///forbidden");
        }
    }

    /** 验证常见脚本和表达式语法不能进入静态 DSL。 */
    @Test
    void shouldRejectExecutableExpressionMarkers() {
        for (String marker : List.of("${bean.value}", "#{service.run()}", "<% code %>",
                "javascript:alert(1)", "groovy:execute")) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(minimal(marker))))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列")
                    .hasMessageNotContaining(marker);
        }
    }

    /** 验证安全预扫描只识别 XML 语法，不误伤普通报表正文。 */
    @Test
    void shouldAllowSecurityKeywordsAsPlainText() {
        List<String> validSources = List.of(
                minimal("schemaLocation = 配置项，SYSTEM 'demo' 只是普通正文"),
                minimal("<![CDATA[<!DOCTYPE 只是普通正文]]>"),
                """
                <!-- schemaLocation="只是注释" -->
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """);

        for (String source : validSources) {
            assertThat(new XmlTemplateCompiler().compile(template(source)).templateCode())
                    .isEqualTo("contract");
        }
    }

    /** 验证字符引用和 CDATA 不能绕过已解码值的表达式检查。 */
    @Test
    void shouldRejectEncodedExecutableExpressionMarkers() {
        List<String> unsafeSources = List.of(
                minimal("$&#123;bean.value}"),
                minimal("java&#115;cript:alert(1)"),
                minimal("$<![CDATA[{bean.value}]]>"),
                """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1" title="$&#123;bean.value}">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """);

        for (String source : unsafeSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列")
                    .hasMessageNotContaining("bean.value");
        }
    }

    /** 验证模板内容必须是严格合法的 UTF-8。 */
    @Test
    void shouldRejectMalformedUtf8() {
        byte[] invalidUtf8 = new byte[]{'<', 'd', 'o', 'c', (byte) 0xC3, (byte) 0x28};
        PrintTemplate template = new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 7, 1, invalidUtf8);

        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("contract")
                .hasMessageNotContaining("�");
    }

    /** 验证 DSL 命名空间、标签和属性必须来自中央白名单。 */
    @Test
    void shouldRejectUnknownNamespaceTagAndAttribute() {
        List<String> invalidSources = List.of(
                """
                <document xmlns="urn:other" context-version="1">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><script>正文</script></page>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1" onclick="run()">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """,
                """
                <Document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>正文</paragraph></page>
                </Document>
                """);

        for (String source : invalidSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证固定父子关系、唯一 page 和空元素约束。 */
    @Test
    void shouldRejectInvalidElementStructure() {
        List<String> invalidSources = List.of(
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <paragraph>正文</paragraph>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page/><page/>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-break>不允许的正文</page-break></page>
                </document>
                """);

        for (String source : invalidSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证 DSL 声明的上下文版本必须与模板快照一致。 */
    @Test
    void shouldRequireMatchingDeclaredContextVersion() {
        String source = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="2">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """;

        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("context-version");
    }

    /** 验证节点深度和节点总量由中央 Governor 限制。 */
    @Test
    void shouldLimitNodeDepthAndCount() {
        String deep = "<section>".repeat(XmlDsl.MAX_NODE_DEPTH)
                + "<paragraph>正文</paragraph>"
                + "</section>".repeat(XmlDsl.MAX_NODE_DEPTH);
        String tooDeep = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>%s</page>
                </document>
                """.formatted(deep);
        String tooMany = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>%s</page>
                </document>
                """.formatted("<page-break/>".repeat(XmlDsl.MAX_NODE_COUNT));

        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(tooDeep)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("深度");
        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(tooMany)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("节点数量");
    }

    /** 验证单个连续文本片段的 Governor 边界没有偏一错误。 */
    @Test
    void shouldLimitTextLength() {
        String atLimit = "a".repeat(XmlDsl.MAX_TEXT_CHARACTERS);
        String overLimit = atLimit + "a";

        assertThat(new XmlTemplateCompiler().compile(template(minimal(atLimit))).templateCode())
                .isEqualTo("contract");
        assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(minimal(overLimit))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("文本长度");
    }

    /** 验证静态页面、层级、ID 和元数据错误在编译阶段失败。 */
    @Test
    void shouldValidateStaticAttributesDuringCompilation() {
        List<String> invalidSources = List.of(
                document("<page size=\"LEGAL\"><paragraph>正文</paragraph></page>"),
                document("<page orientation=\"diagonal\"><paragraph>正文</paragraph></page>"),
                document("<page margin=\"-1mm\"><paragraph>正文</paragraph></page>"),
                document("<page margin=\"9999mm\"><paragraph>正文</paragraph></page>"),
                document("<page><heading level=\"7\">正文</heading></page>"),
                document("<page><paragraph id=\"../bad\">正文</paragraph></page>"),
                """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1" title=" ">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """,
                """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1" language="%s">
                    <page><paragraph>正文</paragraph></page>
                </document>
                """.formatted("x".repeat(36)));

        for (String source : invalidSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证畸形 XML 和静态空容器都在编译期返回安全位置。 */
    @Test
    void shouldReportLocatedMalformedAndIncompleteStructures() {
        List<String> invalidSources = List.of(
                """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>未闭合</page>
                </document>
                """,
                document("<page><section/></page>"),
                document("<page><heading level=\"1\"/></page>"),
                document("<page><heading>   </heading></page>"),
                document("<page><heading><text> \n </text></heading></page>"));

        for (String source : invalidSources) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(template(source)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 包装指定 page 为完整文档。 */
    private String document(String page) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
    }

    /** 将正文放入最小合法模板。 */
    private String minimal(String text) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>%s</paragraph></page>
                </document>
                """.formatted(text);
    }

    /** 创建阶段测试使用的模板快照。 */
    private PrintTemplate template(String xml) {
        return new PrintTemplate(
                "contract",
                TemplateFormat.LETOOL_XML,
                1,
                7,
                1,
                xml.getBytes(StandardCharsets.UTF_8));
    }
}
