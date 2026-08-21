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
