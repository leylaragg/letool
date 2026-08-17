package com.github.leyland.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 根级目录标签的编译和绑定约束测试。
 *
 * @author leyland
 */
class XmlTableOfContentsTest {

    /** 显式目录属性会绑定为通用目录节点。 */
    @Test
    void shouldBindExplicitTableOfContents() {
        DocumentModel document = bind(page("""
                <table-of-contents title="目录" min-level="1" max-level="2"/>
                <heading level="2">第一章</heading>
                """));

        assertThat(document.blocks().get(0))
                .isEqualTo(new TableOfContentsNode("目录", 1, 2));
    }

    /** 省略目录属性时使用通用默认层级且不写死标题。 */
    @Test
    void shouldUseTableOfContentsDefaults() {
        DocumentModel document = bind(page("""
                <table-of-contents/>
                <heading>第一章</heading>
                """));

        TableOfContentsNode contents = (TableOfContentsNode) document.blocks().get(0);
        assertThat(contents.title()).isNull();
        assertThat(contents.minLevel()).isEqualTo(1);
        assertThat(contents.maxLevel()).isEqualTo(3);
    }

    /** 目录不能携带正文、未知属性或非法标题层级。 */
    @Test
    void shouldRejectInvalidTableOfContentsSyntax() {
        for (String invalid : new String[]{
                "<table-of-contents unknown=\"x\"/>",
                "<table-of-contents>正文</table-of-contents>",
                "<table-of-contents><field path=\"title\"/></table-of-contents>",
                "<table-of-contents min-level=\"0\"/>",
                "<table-of-contents max-level=\"7\"/>",
                "<table-of-contents min-level=\"4\" max-level=\"3\"/>"}) {
            assertThatThrownBy(() -> compile(page(
                    invalid + "<heading>第一章</heading>")))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("table-of-contents");
        }
    }

    /** 目录只能由 page 直接声明，动态和章节容器都不能生成目录。 */
    @Test
    void shouldRejectNestedTableOfContents() {
        for (String nested : new String[]{
                "<section><table-of-contents/><heading>第一章</heading></section>",
                "<if path=\"enabled\"><table-of-contents/><heading>第一章</heading></if>",
                "<for-each items=\"items\" var=\"item\"><table-of-contents/><heading>第一章</heading></for-each>"}) {
            assertThatThrownBy(() -> compile(page(nested)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("table-of-contents");
        }
    }

    /** 重复目录和没有后续匹配标题会在最终文档校验时失败。 */
    @Test
    void shouldRejectInvalidBoundTableOfContents() {
        assertThatThrownBy(() -> bind(page("""
                <table-of-contents/>
                <table-of-contents/>
                <heading>第一章</heading>
                """)))
                .hasMessageContaining("目录");
        assertThatThrownBy(() -> bind(page("""
                <table-of-contents min-level="2" max-level="3"/>
                <heading level="1">第一章</heading>
                """)))
                .hasMessageContaining("可收录标题");
    }

    /** 编译并绑定一个不依赖业务数据的目录模板。 */
    private static DocumentModel bind(String xml) {
        return new XmlTemplateBinder().bind(
                compile(xml),
                PrintContext.of(1, JsonNodeFactory.instance.objectNode()));
    }

    /** 编译一个完整 XML 模板。 */
    private static CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "toc", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** 把目录片段包装为完整 page。 */
    private static String page(String content) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>%s</page>
                </document>
                """.formatted(content);
    }
}
