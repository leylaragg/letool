package com.github.leyland.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 动态绑定 Governor 的边界测试。
 *
 * @author leyland
 */
class XmlBindingGovernorTest {

    /** 验证节点和文本计数允许恰好上限并拒绝超限。 */
    @Test
    void shouldEnforceGeneratedNodeAndTextLimits() {
        BindingGovernor governor = new BindingGovernor("contract");

        governor.addNodes(XmlDsl.MAX_GENERATED_NODES);
        governor.addText(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS);

        assertThatThrownBy(() -> governor.addNodes(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract");
        assertThatThrownBy(() -> governor.addText(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract");
    }

    /** 验证循环数量和动态深度允许恰好上限并拒绝下一次增长。 */
    @Test
    void shouldEnforceLoopAndDynamicDepthBoundaries() {
        BindingGovernor governor = new BindingGovernor("contract");

        governor.checkLoopItems(XmlDsl.MAX_LOOP_ITEMS);
        governor.addDynamicOperations(XmlDsl.MAX_DYNAMIC_OPERATIONS);
        for (int depth = 0; depth < XmlDsl.MAX_DYNAMIC_DEPTH; depth++) {
            governor.enterDynamic();
        }

        assertThatThrownBy(() -> governor.checkLoopItems(XmlDsl.MAX_LOOP_ITEMS + 1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("循环元素");
        assertThatThrownBy(governor::enterDynamic)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("动态");
        assertThatThrownBy(() -> governor.addDynamicOperations(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("动态操作");
    }

    /** 验证嵌套循环即使不生成文档节点也受累计动态操作上限保护。 */
    @Test
    void shouldLimitAccumulatedDynamicOperations() {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("active", false);
        for (int index = 0; index < 225; index++) {
            root.withArray("outer").add(index);
            root.withArray("inner").add(index);
        }
        CompiledXmlTemplate template = compile("""
                <page>
                    <for-each items="outer" var="outerItem">
                        <for-each items="inner" var="innerItem">
                            <if path="active" operator="truthy"><paragraph>不应生成</paragraph></if>
                        </for-each>
                    </for-each>
                </page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("累计动态操作")
                .hasMessageContaining("contract");
    }

    /** 验证真实绑定入口会统计循环重复生成的块节点和行内节点。 */
    @Test
    void shouldLimitGeneratedNodesThroughBinder() {
        ObjectNode root = squareLoopContext(184);
        CompiledXmlTemplate template = compile("""
                <page><for-each items="outer" var="outerItem">
                    <for-each items="inner" var="innerItem">
                        <paragraph>序号：<field path="$innerItem"/></paragraph>
                    </for-each>
                </for-each></page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("生成节点")
                .hasMessageContaining("contract");
    }

    /** 验证真实绑定入口会统计循环重复生成的静态文本。 */
    @Test
    void shouldLimitGeneratedTextThroughBinder() {
        ObjectNode root = squareLoopContext(201);
        String text = "文本".repeat(25);
        CompiledXmlTemplate template = compile("""
                <page><for-each items="outer" var="outerItem">
                    <for-each items="inner" var="innerItem"><paragraph>%s</paragraph></for-each>
                </for-each></page>
                """.formatted(text));

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("生成文本")
                .hasMessageContaining("contract");
    }

    /** 验证单循环数组元素超过上限时通过真实绑定入口失败。 */
    @Test
    void shouldLimitSingleLoopItems() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        for (int index = 0; index <= XmlDsl.MAX_LOOP_ITEMS; index++) {
            root.withArray("items").add(index);
        }
        CompiledXmlTemplate template = compile("""
                <page><for-each items="items" var="item"><paragraph><field path="$item"/></paragraph></for-each></page>
                """);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("循环元素")
                .hasMessageContaining("contract");
    }

    /** 验证动态控制结构嵌套超过上限时在绑定期失败。 */
    @Test
    void shouldLimitDynamicDepth() {
        String body = "<paragraph>正文</paragraph>";
        for (int index = 0; index <= XmlDsl.MAX_DYNAMIC_DEPTH; index++) {
            body = "<if path=\"active\" operator=\"truthy\">" + body + "</if>";
        }
        CompiledXmlTemplate template = compile("<page>" + body + "</page>");
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("active", true);

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(1, root)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("动态")
                .hasMessageContaining("contract");
    }

    /** 创建两个等长数组组成的嵌套循环上下文。 */
    private ObjectNode squareLoopContext(int size) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        for (int index = 0; index < size; index++) {
            root.withArray("outer").add(index);
            root.withArray("inner").add(index);
        }
        return root;
    }

    /** 编译指定页面内容。 */
    private CompiledXmlTemplate compile(String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
