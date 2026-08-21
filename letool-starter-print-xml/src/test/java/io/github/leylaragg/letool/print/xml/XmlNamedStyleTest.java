package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TextAlignment;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 命名样式声明和类型化引用测试。
 *
 * @author leyland
 */
class XmlNamedStyleTest {

    /** 四类声明应编译为核心样式表，节点只保存对应类型的样式名。 */
    @Test
    void shouldCompileNamedStylesAndReferences() {
        DocumentModel document = bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <styles>
                        <paragraph-style name="body" text-style="body-text"
                                         alignment="justify" whitespace="preserve-line-breaks"
                                         wrap="break-long-words" keep-together="true"/>
                        <text-style name="body-text" font-family="Noto Sans CJK SC"
                                    font-size="11pt" font-weight="bold" color="#123456"
                                    line-height="1.5" decorations="underline,line-through"/>
                        <table-style name="details" width="100%" layout="fixed"
                                     column-widths="30%,70%" repeat-header="true"
                                     page-break="keep-rows"/>
                        <cell-style name="header-cell" background="#F2F2F2"
                                    padding="1mm 2mm 3mm 4mm" vertical-alignment="middle">
                            <border side="all" line="solid" width="0.5pt" color="#333333"/>
                        </cell-style>
                    </styles>
                    <page>
                        <page-body>
                            <paragraph style="body">正文</paragraph>
                            <table style="details">
                                <header><row>
                                    <cell style="header-cell"><paragraph>标题</paragraph></cell>
                                    <cell style="header-cell"><paragraph>说明</paragraph></cell>
                                </row></header>
                                <body><row>
                                    <cell><paragraph>内容</paragraph></cell>
                                    <cell><paragraph>详情</paragraph></cell>
                                </row></body>
                            </table>
                        </page-body>
                    </page>
                </document>
                """);

        assertThat(document.styleSheet().hasNamedStyles()).isTrue();
        assertThat(document.styleSheet().text("body-text").orElseThrow().fontWeight())
                .isEqualTo(FontWeight.BOLD);
        assertThat(document.styleSheet().text("body-text").orElseThrow().color())
                .isEqualTo(DocumentColor.rgb(18, 52, 86));
        assertThat(document.styleSheet().paragraph("body").orElseThrow().alignment())
                .isEqualTo(TextAlignment.JUSTIFY);
        assertThat(document.styleSheet().paragraph("body").orElseThrow().whitespaceMode())
                .isEqualTo(WhitespaceMode.PRESERVE_LINE_BREAKS);
        assertThat(document.styleSheet().paragraph("body").orElseThrow().textWrapMode())
                .isEqualTo(TextWrapMode.BREAK_LONG_WORDS);
        assertThat(document.styleSheet().table("details").orElseThrow().layoutMode())
                .isEqualTo(TableLayoutMode.FIXED);
        assertThat(document.styleSheet().cell("header-cell").orElseThrow()
                .topBorder().lineStyle()).isEqualTo(BorderLineStyle.SOLID);
        assertThat((ParagraphNode) document.pageSequences().get(0).body().get(0))
                .extracting(ParagraphNode::styleName).isEqualTo("body");
    }

    /** 样式引用类型不匹配、重复声明和未知属性应在编译期失败。 */
    @Test
    void shouldRejectInvalidStyleDeclarations() {
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <styles><text-style name="body"/></styles>
                    <page><page-body><paragraph style="body">正文</paragraph></page-body></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <styles><text-style name="body"/><text-style name="body"/></styles>
                    <page><page-body/></page>
                </document>
                """))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> bind("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <styles><text-style name="body" css="display:none"/></styles>
                    <page><page-body/></page>
                </document>
                """))
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
