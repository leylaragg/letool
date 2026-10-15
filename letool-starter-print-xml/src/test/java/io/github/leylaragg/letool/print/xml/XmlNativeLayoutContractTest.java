package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 最终报告原生排版所需的受控 XML 属性。 */
class XmlNativeLayoutContractTest {
    @Test
    void shouldCompileNativeFontAndMeasuredRegionContract() {
        String source = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1"
                          default-font-family="Times New Roman" glyph-fallback-family="SimSun"
                          full-font-embedding="true" web-optimized="true">
                    <styles>
                        <text-style name="rich" font-size="12pt" font-style="italic"/>
                        <paragraph-style name="title" text-style="rich" keep-with-next="true"/>
                    </styles>
                    <page margin-top="19.05mm" margin-right="13mm" margin-bottom="36.689mm" margin-left="25mm"
                          header-gap="8pt" body-alignment="middle" max-pages="1">
                        <page-header placement="page-top" offset="54pt"><paragraph>Header</paragraph></page-header>
                        <page-body><paragraph style="title">Native report</paragraph></page-body>
                        <page-footer placement="page-bottom" anchor="first-baseline"
                            offset="60pt"><paragraph>Footer</paragraph></page-footer>
                    </page>
                </document>
                """;
        PrintTemplate template = new PrintTemplate("native-layout", TemplateFormat.LETOOL_XML, 1, 1, 1,
                source.getBytes(StandardCharsets.UTF_8));
        assertThat(new XmlTemplateCompiler().compile(template).templateCode()).isEqualTo("native-layout");
    }
    @Test
    void shouldCompileMeasuredContentsPresentation() {
        String source = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body><table-of-contents id="contents" title="Contents"
                        title-baseline="66pt" first-entry-baseline="103pt" continuation-baseline="67pt"
                        group-height="34pt" entry-height="28pt" level-indent="14pt" page-inset="6pt"
                        leader-start="8pt" leader-end="10pt" leader-offset="3pt" leader-width="0.65pt"
                        leader-dash="0.4pt" leader-gap="2.6pt" group-rule-offset="10pt"
                        group-rule-width="0.5pt" group-rule-color="#A6A6A6" entry-text-style=""/>
                    </page-body></page>
                </document>
                """;
        PrintTemplate template = new PrintTemplate("measured-contents", TemplateFormat.LETOOL_XML, 1, 1, 1,
                source.getBytes(StandardCharsets.UTF_8));
        assertThat(new XmlTemplateCompiler().compile(template).templateCode()).isEqualTo("measured-contents");
    }
}
