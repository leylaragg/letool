package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 显式条件分支以及缺失、空值和空集合边界测试。
 *
 * @author leyland
 */
class XmlConditionalBranchTest {

    /** 条件只绑定命中的分支，then 和 else 本身不进入文档模型。 */
    @Test
    void shouldBindExplicitThenOrElseBranch() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body>
                        <if path="enabled" operator="truthy">
                            <then><paragraph>启用</paragraph></then>
                            <else><paragraph>停用</paragraph></else>
                        </if>
                    </page-body></page>
                </document>
                """);

        assertThat(body(template, JsonNodeFactory.instance.objectNode().put("enabled", true)))
                .containsExactly(new ParagraphNode("", java.util.List.of(new TextNode("启用"))));
        assertThat(body(template, JsonNodeFactory.instance.objectNode().put("enabled", false)))
                .containsExactly(new ParagraphNode("", java.util.List.of(new TextNode("停用"))));
    }

    /** 新空值操作符应保持 missing、显式 null 和空白字符串的差异。 */
    @Test
    void shouldDistinguishMissingNullAndEmptyValues() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putNull("nullable");
        data.put("blank", "   ");
        data.put("number", 1);
        data.putArray("items");

        assertThat(text(bind("nullable", "is-null", data))).isEqualTo("是");
        assertThat(text(bind("nullable", "empty", data))).isEqualTo("是");
        assertThat(text(bind("items", "empty", data))).isEqualTo("是");
        assertThat(text(bind("blank", "empty", data))).isEqualTo("否");
        assertThat(text(bind("missing", "not-exists", data))).isEqualTo("是");
        assertThatThrownBy(() -> bind("missing", "is-null", data))
                .isInstanceOf(PrintValidationException.class);
        assertThat(text(bind("blank", "is-null", data))).isEqualTo("否");
        assertThatThrownBy(() -> bind("number", "empty", data))
                .isInstanceOf(PrintValidationException.class);
    }

    /** if 必须使用 then 外壳，分支顺序和数量都应确定。 */
    @Test
    void shouldRejectLegacyOrMalformedBranches() {
        assertThatThrownBy(() -> compile(document(
                "<if path=\"enabled\" operator=\"truthy\"><paragraph>旧写法</paragraph></if>")))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile(document(
                "<if path=\"enabled\" operator=\"truthy\"><else><paragraph>否</paragraph></else></if>")))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile(document(
                "<if path=\"enabled\" operator=\"truthy\"><then/><else><paragraph>否</paragraph></else></if>")))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 构造只改变路径和操作符的条件文档并绑定。 */
    private DocumentModel bind(String path, String operator, ObjectNode data) {
        return new XmlTemplateBinder().bind(compile(document("""
                <if path="%s" operator="%s">
                    <then><paragraph>是</paragraph></then>
                    <else><paragraph>否</paragraph></else>
                </if>
                """.formatted(path, operator))), PrintContext.of(1, data));
    }

    /** 返回单页正文。 */
    private java.util.List<io.github.leylaragg.letool.print.document.node.BlockNode> body(
            CompiledXmlTemplate template, ObjectNode data) {
        return new XmlTemplateBinder().bind(template, PrintContext.of(1, data))
                .pageSequences().get(0).body();
    }

    /** 读取条件样本生成的首段文本。 */
    private String text(DocumentModel document) {
        ParagraphNode paragraph = (ParagraphNode) document.pageSequences().get(0).body().get(0);
        return ((TextNode) paragraph.children().get(0)).text();
    }

    /** 把块级样本放入新页面正文。 */
    private String document(String content) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><page-body>%s</page-body></page>
                </document>
                """.formatted(content);
    }

    /** 编译条件测试模板。 */
    private CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 7, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
