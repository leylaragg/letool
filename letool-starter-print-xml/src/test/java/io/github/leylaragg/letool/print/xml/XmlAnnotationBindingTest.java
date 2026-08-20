package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 批注标签的编译、数据绑定和引用约束测试。
 *
 * @author leyland
 */
class XmlAnnotationBindingTest {

    /** 验证便签正文可以混排静态文本和受控字段。 */
    @Test
    void shouldBindTextNoteWithDynamicContent() {
        CompiledXmlTemplate template = compile(page("""
                <paragraph id="summary">正文</paragraph>
                <annotation type="text-note" target="summary" author="审核人">请复核 <field path="review.reason"/></annotation>
                """));
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putObject("review").put("reason", "缺少签名");

        DocumentModel document = new XmlTemplateBinder()
                .bind(template, PrintContext.of(1, data));

        assertThat(XmlTestDocuments.body(document)).hasSize(2);
        assertThat(XmlTestDocuments.body(document).get(1)).isEqualTo(new AnnotationNode(
                AnnotationType.TEXT_NOTE,
                "summary",
                AnnotationPlacement.TOP_RIGHT,
                6_000,
                6_000,
                0,
                0,
                "审核人",
                "请复核 缺少签名"));
    }

    /** 验证自由文本框使用受控毫米尺寸、方位和带方向偏移。 */
    @Test
    void shouldBindFreeTextGeometry() {
        CompiledXmlTemplate template = compile(page("""
                <paragraph id="summary">正文</paragraph>
                <annotation type="free-text" target="summary" placement="bottom-left"
                            width="50mm" height="20mm" offset-x="1.5mm" offset-y="-2mm">文本框内容</annotation>
                """));

        DocumentModel document = new XmlTemplateBinder()
                .bind(template, PrintContext.of(1, JsonNodeFactory.instance.objectNode()));
        AnnotationNode annotation = (AnnotationNode) XmlTestDocuments.body(document).get(1);

        assertThat(annotation.type()).isEqualTo(AnnotationType.FREE_TEXT);
        assertThat(annotation.placement()).isEqualTo(AnnotationPlacement.BOTTOM_LEFT);
        assertThat(annotation.widthMicrometers()).isEqualTo(50_000);
        assertThat(annotation.heightMicrometers()).isEqualTo(20_000);
        assertThat(annotation.offsetXMicrometers()).isEqualTo(1_500);
        assertThat(annotation.offsetYMicrometers()).isEqualTo(-2_000);
        assertThat(annotation.author()).isEmpty();
        assertThat(annotation.content()).isEqualTo("文本框内容");
    }

    /** 验证批注标签拒绝未知语义、越界几何和非文本子标签。 */
    @Test
    void shouldRejectInvalidAnnotationDeclaration() {
        for (String invalid : new String[]{
                "<annotation target=\"summary\">正文</annotation>",
                "<annotation type=\"popup\" target=\"summary\">正文</annotation>",
                "<annotation type=\"text-note\" target=\"bad target\">正文</annotation>",
                "<annotation type=\"text-note\" target=\"summary\" placement=\"center\">正文</annotation>",
                "<annotation type=\"free-text\" target=\"summary\" width=\"0mm\">正文</annotation>",
                "<annotation type=\"free-text\" target=\"summary\" offset-x=\"2000.001mm\">正文</annotation>",
                "<annotation type=\"text-note\" target=\"summary\"></annotation>",
                "<annotation type=\"text-note\" target=\"summary\"><link target=\"summary\">跳转</link></annotation>",
                "<annotation type=\"text-note\" target=\"summary\" action=\"https://example.com\">正文</annotation>"}) {
            assertThatThrownBy(() -> compile(page(
                    "<paragraph id=\"summary\">正文</paragraph>" + invalid)))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("annotation");
        }
    }

    /** 验证不存在的目标和字段都安全失败，错误不会回显业务值。 */
    @Test
    void shouldRejectMissingTargetAndFieldWithoutLeakingData() {
        CompiledXmlTemplate missingTarget = compile(page(
                "<annotation type=\"text-note\" target=\"missing\">正文</annotation>"));
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                missingTarget, PrintContext.of(1, JsonNodeFactory.instance.objectNode())))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");

        CompiledXmlTemplate missingField = compile(page("""
                <paragraph id="summary">正文</paragraph>
                <annotation type="text-note" target="summary"><field path="review.reason"/></annotation>
                """));
        ObjectNode data = JsonNodeFactory.instance.objectNode().put("secret", "business-value");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                missingField, PrintContext.of(1, data)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("不存在")
                .hasMessageNotContaining("business-value");
    }

    /** 将页内节点包装为完整模板。 */
    private static String page(String content) {
        return """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    <page>%s</page>
                </document>
                """.formatted(content);
    }

    /** 编译一个批注测试模板。 */
    private static CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "annotation", TemplateFormat.LETOOL_XML, 1, 1, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
