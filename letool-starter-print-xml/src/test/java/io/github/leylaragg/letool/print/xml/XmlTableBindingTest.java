package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 表格结构编译与动态行绑定测试。
 *
 * @author leyland
 */
class XmlTableBindingTest {

    /** 验证表头和循环产生的完整表体行能够绑定为严格表格。 */
    @Test
    void shouldBindHeaderAndDynamicBodyRows() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><table id="items">
                        <header><row>
                            <cell><paragraph>名称</paragraph></cell>
                            <cell><paragraph>金额</paragraph></cell>
                        </row></header>
                        <body><for-each items="items" var="item"><row>
                            <cell><paragraph><field path="$item.name"/></paragraph></cell>
                            <cell><paragraph><field path="$item.amount" formatter="number"/></paragraph></cell>
                        </row></for-each></body>
                    </table></page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode items = root.putArray("items");
        items.addObject().put("name", "纸张").put("amount", 12.50);
        items.addObject().put("name", "墨盒").put("amount", 88);

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(1, root));
        TableNode table = (TableNode) XmlTestDocuments.body(document).get(0);

        assertThat(table.id()).isEqualTo("items");
        assertThat(table.headerRowCount()).isEqualTo(1);
        assertThat(table.rows()).hasSize(3);
        ParagraphNode firstName = (ParagraphNode) table.rows().get(1).cells().get(0).content().get(0);
        assertThat(firstName.children()).containsExactly(new TextNode("纸张"));
    }

    /** 验证无表头且动态行为空时会剪枝整个表格。 */
    @Test
    void shouldPruneTableWhenNoRowsAreGenerated() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><table><body><for-each items="items" var="item">
                        <row><cell/></row>
                    </for-each></body></table></page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putArray("items");

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(1, root));
        assertThat(XmlTestDocuments.body(document)).isEmpty();
    }

    /** 验证表体为空时仍可保留仅包含表头的表格。 */
    @Test
    void shouldKeepHeaderOnlyTableAfterBinding() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><table>
                        <header><row><cell/></row></header>
                        <body><if path="visible" operator="eq" value="true" value-type="boolean">
                            <row><cell/></row>
                        </if></body>
                    </table></page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("visible", false);

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(1, root));
        TableNode table = (TableNode) XmlTestDocuments.body(document).get(0);

        assertThat(table.headerRowCount()).isEqualTo(1);
        assertThat(table.rows()).hasSize(1);
    }

    /** 验证表格分区、顺序和动态结果域在编译阶段受到约束。 */
    @Test
    void shouldRejectInvalidTableStructureAtCompilation() {
        assertThatThrownBy(() -> compile(page("<table><header><row><cell/></row></header></table>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("body");
        assertThatThrownBy(() -> compile(page("""
                <table><body><row><cell/></row></body>
                    <header><row><cell/></row></header></table>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("顺序");
        assertThatThrownBy(() -> compile(page("""
                <table><body><if path="visible" operator="exists">
                    <paragraph>非法块</paragraph>
                </if></body></table>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("row");
        assertThatThrownBy(() -> compile(page("""
                <if path="visible" operator="exists"><row><cell/></row></if>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("row 只能");
    }

    /** 验证动态展开后的非法网格仍由核心模型拒绝。 */
    @Test
    void shouldRejectInvalidGridAfterDynamicBinding() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><table><body>
                        <row><cell row-span="2"/><cell/></row>
                        <if path="visible" operator="eq" value="true" value-type="boolean">
                            <row><cell col-span="2"/></row>
                        </if>
                    </body></table></page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("visible", true);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("网格");
    }

    /** 将页内片段包装为完整模板。 */
    private static String page(String content) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>%s</page>
                </document>
                """.formatted(content);
    }

    /** 编译测试模板。 */
    private static CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "table", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
