package com.github.leyland.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.xml.format.FormatCompileContext;
import com.github.leyland.letool.print.xml.format.PrintFormatPlan;
import com.github.leyland.letool.print.xml.format.PrintFormatterRegistry;
import com.github.leyland.letool.print.xml.format.PrintValueFormatter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 字段格式计划的编译与绑定测试。
 *
 * @author leyland
 */
class XmlFormattedFieldTest {

    /** 验证内置数字格式计划会在绑定阶段产生稳定文本。 */
    @Test
    void shouldBindFieldWithCompiledNumberPlan() {
        CompiledXmlTemplate template = compile(new XmlTemplateCompiler(), """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph><field path="amount" formatter="number">
                        <format-option name="pattern" value="#,##0.00"/>
                        <format-option name="locale" value="en-US"/>
                    </field></paragraph></page>
                </document>
                """);

        ParagraphNode paragraph = (ParagraphNode) new XmlTemplateBinder().bind(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode().put("amount", 1234.5)))
                .blocks().get(0);

        assertThat(paragraph.children()).containsExactly(new TextNode("1,234.50"));
    }

    /** 验证 null 字段直接输出空文本且不会调用格式化计划。 */
    @Test
    void shouldSkipFormatterForNullValue() {
        AtomicInteger calls = new AtomicInteger();
        PrintValueFormatter formatter = formatter("custom", value -> {
            calls.incrementAndGet();
            return "formatted";
        });
        CompiledXmlTemplate template = compile(
                new XmlTemplateCompiler(new PrintFormatterRegistry(List.of(formatter))), """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph><field path="value" formatter="custom"/></paragraph></page>
                </document>
                """);

        ParagraphNode paragraph = (ParagraphNode) new XmlTemplateBinder().bind(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode().putNull("value")))
                .blocks().get(0);

        assertThat(paragraph.children()).containsExactly(new TextNode(""));
        assertThat(calls).hasValue(0);
    }

    /** 验证格式化器只在编译阶段查询并生成一次计划。 */
    @Test
    void shouldFreezeCustomFormatterPlanAtCompilation() {
        AtomicInteger compilations = new AtomicInteger();
        PrintValueFormatter formatter = new PrintValueFormatter() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public PrintFormatPlan compile(
                    Map<String, String> options, FormatCompileContext context) {
                compilations.incrementAndGet();
                String prefix = options.get("prefix");
                return value -> prefix + value.asText();
            }
        };
        CompiledXmlTemplate template = compile(
                new XmlTemplateCompiler(new PrintFormatterRegistry(List.of(formatter))), """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph><field path="value" formatter="custom">
                        <format-option name="prefix" value="ID-"/>
                    </field></paragraph></page>
                </document>
                """);

        new XmlTemplateBinder().bind(template, context("A"));
        ParagraphNode second = (ParagraphNode) new XmlTemplateBinder()
                .bind(template, context("B")).blocks().get(0);

        assertThat(compilations).hasValue(1);
        assertThat(second.children()).containsExactly(new TextNode("ID-B"));
    }

    /** 验证无格式化器、未知格式化器和重复选项都会在编译阶段失败。 */
    @Test
    void shouldRejectInvalidFormatterDeclarations() {
        assertThatThrownBy(() -> compile(new XmlTemplateCompiler(), fieldXml(
                "<field path=\"value\"><format-option name=\"pattern\" value=\"0\"/></field>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("formatter");
        assertThatThrownBy(() -> compile(new XmlTemplateCompiler(), fieldXml(
                "<field path=\"value\" formatter=\"missing\"/>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> compile(new XmlTemplateCompiler(), fieldXml("""
                <field path="value" formatter="number">
                    <format-option name="pattern" value="0"/>
                    <format-option name="pattern" value="00"/>
                </field>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("重复");
        assertThatThrownBy(() -> compile(new XmlTemplateCompiler(), fieldXml("""
                <field path="value" formatter="number">
                    <format-option name="pattern" value=""/>
                </field>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("value 不能为空");
    }

    /** 验证自定义格式化器异常不能把业务值或实现消息带入编译错误。 */
    @Test
    void shouldHideCustomFormatterImplementationMessage() {
        PrintValueFormatter formatter = new PrintValueFormatter() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public PrintFormatPlan compile(
                    Map<String, String> options, FormatCompileContext context) {
                throw new IllegalArgumentException("secret-implementation-detail");
            }
        };

        assertThatThrownBy(() -> compile(
                new XmlTemplateCompiler(new PrintFormatterRegistry(List.of(formatter))),
                fieldXml("<field path=\"value\" formatter=\"custom\"/>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("格式化器配置无效")
                .hasMessageNotContaining("secret-implementation-detail");
    }

    /** 验证自定义计划抛出的打印异常同样会被转换为安全绑定错误。 */
    @Test
    void shouldHideCustomPlanPrintValidationMessage() {
        PrintValueFormatter formatter = formatter("custom", value -> {
            throw com.github.leyland.letool.print.exception.PrintValidationException
                    .invalidDocument("secret-business-value");
        });
        CompiledXmlTemplate template = compile(
                new XmlTemplateCompiler(new PrintFormatterRegistry(List.of(formatter))),
                fieldXml("<field path=\"value\" formatter=\"custom\"/>"));

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, context("visible")))
                .isInstanceOf(com.github.leyland.letool.print.exception.PrintValidationException.class)
                .hasMessageContaining("字段值无法按已编译格式输出")
                .hasMessageNotContaining("secret-business-value");
    }

    /** 验证同一内置格式计划可由多个线程并发绑定不同上下文。 */
    @Test
    void shouldReuseBuiltInFormatPlanConcurrently() {
        CompiledXmlTemplate template = compile(new XmlTemplateCompiler(), """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph><field path="value" formatter="number">
                        <format-option name="pattern" value="0000"/>
                    </field></paragraph></page>
                </document>
                """);

        List<String> values = IntStream.range(0, 100).parallel()
                .mapToObj(index -> {
                    ObjectNode root = JsonNodeFactory.instance.objectNode().put("value", index);
                    ParagraphNode paragraph = (ParagraphNode) new XmlTemplateBinder()
                            .bind(template, PrintContext.of(1, root)).blocks().get(0);
                    return ((TextNode) paragraph.children().get(0)).text();
                })
                .sorted()
                .toList();

        assertThat(values).containsExactlyElementsOf(
                IntStream.range(0, 100).mapToObj(index -> "%04d".formatted(index)).toList());
    }

    /** 创建简单自定义格式化器。 */
    private static PrintValueFormatter formatter(String name, PrintFormatPlan plan) {
        return new PrintValueFormatter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public PrintFormatPlan compile(
                    Map<String, String> options, FormatCompileContext context) {
                return plan;
            }
        };
    }

    /** 创建测试上下文。 */
    private static PrintContext context(String value) {
        return PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("value", value));
    }

    /** 将字段片段包装为完整模板。 */
    private static String fieldXml(String field) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph>%s</paragraph></page>
                </document>
                """.formatted(field);
    }

    /** 编译测试模板。 */
    private static CompiledXmlTemplate compile(XmlTemplateCompiler compiler, String xml) {
        return compiler.compile(new PrintTemplate(
                "formatted-field", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
