package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageSize;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 多页面序列、布局和逻辑页码的绑定测试。
 *
 * @author leyland
 */
class XmlPageSequenceBindingTest {

    /** 每个 page 应形成独立页面序列，并保留自己的布局和页码规则。 */
    @Test
    void shouldBindMultiplePageSequences() {
        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page size="LETTER" orientation="landscape"
                          margin-top="10mm" margin-right="11mm"
                          margin-bottom="12mm" margin-left="13mm">
                        <page-body><paragraph>首页</paragraph></page-body>
                    </page>
                    <page size="A4" margin="18mm" numbering="restart" start-page-number="7">
                        <page-body><paragraph>正文</paragraph></page-body>
                    </page>
                    <page numbering="excluded">
                        <page-body><paragraph>附录</paragraph></page-body>
                    </page>
                </document>
                """);

        assertThat(document.pageSequences()).hasSize(3);
        assertThat(document.pageSequences().get(0).pageLayout().pageSize())
                .isEqualTo(PageSize.LETTER);
        assertThat(document.pageSequences().get(0).pageLayout().orientation())
                .isEqualTo(PageOrientation.LANDSCAPE);
        assertThat(document.pageSequences().get(0).pageLayout().margins())
                .isEqualTo(new Margins(10_000, 11_000, 12_000, 13_000));
        assertThat(document.pageSequences().get(1).pageNumbering().restartAt())
                .hasValue(7);
        assertThat(document.pageSequences().get(2).pageNumbering().includedInCount())
                .isFalse();
    }

    /** 不完整边距、错误页码组合和 excluded 页码节点应在编译期失败。 */
    @Test
    void shouldRejectInvalidPageConfiguration() {
        assertInvalidPage("margin-top=\"10mm\"");
        assertInvalidPage("margin=\"10mm\" margin-left=\"12mm\"");
        assertInvalidPage("numbering=\"restart\"");
        assertInvalidPage("numbering=\"continue\" start-page-number=\"1\"");
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page numbering="excluded">
                        <page-body><paragraph><page-number/></paragraph></page-body>
                    </page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 统一边距使用规范毫米语法，四边独立值仍由通用长度解析器处理。 */
    @Test
    void shouldDistinguishCanonicalUniformMarginFromFlexibleSideMargins() {
        for (String margin : List.of("01mm", "1.0000mm", "1e1mm")) {
            assertThatThrownBy(() -> bind("""
                    <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                        <page margin="%s"><page-body/></page>
                    </document>
                    """.formatted(margin)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("页面边距必须使用非负 mm 单位");
        }

        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page margin-top="01mm" margin-right="1.0000mm"
                          margin-bottom="1e1mm" margin-left="2mm"><page-body/></page>
                </document>
                """);

        assertThat(document.pageSequences().get(0).pageLayout().margins())
                .isEqualTo(new Margins(1_000, 1_000, 10_000, 2_000));
    }

    /** A4 纵向统一边距允许最接近半页宽的微米值，但不能等于半页宽。 */
    @Test
    void shouldEnforceExactA4UniformMarginBoundary() {
        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page size="A4" orientation="portrait" margin="104.999mm"><page-body/></page>
                </document>
                """);

        assertThat(document.pageSequences().get(0).pageLayout().margins())
                .isEqualTo(new Margins(104_999, 104_999, 104_999, 104_999));
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page size="A4" orientation="portrait" margin="105mm"><page-body/></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("页面边距之和必须小于页面边长");
    }

    /** 组装只改变 page 属性的最小文档。 */
    private void assertInvalidPage(String attributes) {
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page %s><page-body/></page>
                </document>
                """.formatted(attributes)))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 编译并绑定空上下文文档。 */
    private DocumentModel bind(String xml) {
        CompiledXmlTemplate template = new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 7, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
        return new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode()));
    }
}
