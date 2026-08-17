package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.ImageNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 图片描述、书签和内部链接绑定测试。
 *
 * @author leyland
 */
class XmlImageNavigationBindingTest {

    /** 验证静态和动态逻辑资源只生成图片描述，不执行资源读取。 */
    @Test
    void shouldBindStaticAndDynamicImageDescriptors() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>
                        <image id="logo" resource-id="brand.logo" alt="公司标识"
                               width="30mm" height="12.5mm"/>
                        <image resource-path="signature.resourceId" alt="签名"
                               width="40mm" height="20mm"/>
                    </page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putObject("signature").put("resourceId", "signature-2026");

        List<io.github.leylaragg.letool.print.document.node.BlockNode> blocks =
                new XmlTemplateBinder().bind(template, PrintContext.of(1, root)).blocks();

        assertThat(blocks).containsExactly(
                new ImageNode("logo", "brand.logo", "公司标识", 30_000, 12_500),
                new ImageNode("", "signature-2026", "签名", 40_000, 20_000));
    }

    /** 验证书签和内部链接标签会保留行内顺序并通过文档级引用校验。 */
    @Test
    void shouldBindBookmarkAndInternalLink() {
        CompiledXmlTemplate template = compile("""
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page><paragraph><bookmark id="summary" label="汇总"/><link target="summary">返回 <field path="caption"/></link></paragraph></page>
                </document>
                """);
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("caption", "首页");

        ParagraphNode paragraph = (ParagraphNode) new XmlTemplateBinder()
                .bind(template, PrintContext.of(1, root)).blocks().get(0);

        assertThat(paragraph.children()).containsExactly(
                new BookmarkNode("summary", "汇总"),
                new InternalLinkNode("summary", List.of(
                        new TextNode("返回 "), new TextNode("首页"))));
    }

    /** 验证图片来源互斥、必填尺寸和空元素约束在编译阶段生效。 */
    @Test
    void shouldRejectInvalidImageDeclaration() {
        assertThatThrownBy(() -> compile(page("""
                <image resource-id="logo" resource-path="logo.path"
                       alt="标识" width="1mm" height="1mm"/>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("只能声明一个");
        assertThatThrownBy(() -> compile(page(
                "<image resource-id=\"logo\" alt=\"标识\" width=\"0mm\" height=\"1mm\"/>")))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("width");
        assertThatThrownBy(() -> compile(page("""
                <image resource-id="logo" alt="标识" width="1mm" height="1mm">
                    <paragraph>非法内容</paragraph>
                </image>
                """)))
                .isInstanceOf(PrintCompilationException.class);
    }

    /** 验证动态图片资源必须存在且是非空字符串，错误不回显业务值。 */
    @Test
    void shouldRejectInvalidDynamicImageResource() {
        CompiledXmlTemplate template = compile(page("""
                <image resource-path="imageId" alt="标识" width="1mm" height="1mm"/>
                """));

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                        .put("imageId", 123456))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("非空字符串")
                .hasMessageNotContaining("123456");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                        .put("imageId", ""))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("非空字符串");

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JsonNodeFactory.instance.objectNode())))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JsonNodeFactory.instance.objectNode().putNull("imageId"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("非空字符串");
        ObjectNode objectResource = JsonNodeFactory.instance.objectNode();
        objectResource.putObject("imageId").put("secret", "hidden");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, objectResource)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("非空字符串")
                .hasMessageNotContaining("hidden");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                template, PrintContext.of(1, JsonNodeFactory.instance.objectNode().put("imageId", true))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("非空字符串");

        CompiledXmlTemplate invalidTraversal = compile(page("""
                <image resource-path="imageId.value" alt="标识" width="1mm" height="1mm"/>
                """));
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                invalidTraversal,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode().put("imageId", "secret"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("无法继续遍历")
                .hasMessageNotContaining("secret");
    }

    /** 验证非法导航结构和不存在的链接目标不会被静默接受。 */
    @Test
    void shouldRejectInvalidNavigation() {
        assertThatThrownBy(() -> compile(page("""
                <paragraph><link target="summary"><bookmark id="summary" label="汇总"/></link></paragraph>
                """)))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile(page("""
                <for-each items="items" var="item">
                    <paragraph><bookmark id="same" label="重复"/></paragraph>
                </for-each>
                """)))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("静态 ID");

        CompiledXmlTemplate missing = compile(page(
                "<paragraph><link target=\"missing\">跳转</link></paragraph>"));
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                missing, PrintContext.of(1, JsonNodeFactory.instance.objectNode())))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
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
                "image-navigation", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
