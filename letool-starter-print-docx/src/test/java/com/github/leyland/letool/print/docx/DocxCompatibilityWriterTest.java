package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.AnnotationPlacement;
import com.github.leyland.letool.print.document.node.AnnotationType;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 DOCX 对图片和 PDF 定位批注采用可选择的兼容策略。
 *
 * @author leyland
 */
class DocxCompatibilityWriterTest {

    /** 兼容模式保留可读内容，并明确报告发生过的两类降级。 */
    @Test
    void shouldRenderImagePlaceholderAndAnnotationEndnoteInCompatibleMode() throws Exception {
        RenderedDocument rendered = compatibleRenderer().render(
                documentWithImageAndAnnotation(), RenderOptions.defaults());
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(rendered.content()));
        String mainXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getJaxbElement(), true, true);
        String endnotesXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getEndNotesPart().getJaxbElement(), true, true);

        assertThat(mainXml)
                .contains("图片替代文字", "endnoteReference")
                .doesNotContain("secret-resource-id");
        assertThat(endnotesXml).contains("审核人", "批注正文");
        assertThat(rendered.metadata())
                .containsEntry("compatibilityMode", "COMPATIBLE")
                .containsEntry("degradedNodeCount", "2")
                .containsEntry("degradedNodeTypes", "annotation,image");
    }

    /** 严格模式在创建 OOXML 包之前拒绝需要降级的节点。 */
    @Test
    void shouldRejectDegradedNodesInStrictMode() {
        DocxRendererOptions options = new DocxRendererOptions(
                DocxCompatibilityMode.STRICT, "Arial", "SimSun", 21);
        DocxDocumentRenderer renderer = new DocxDocumentRenderer(options);

        assertThatThrownBy(() -> renderer.render(
                documentWithImageAndAnnotation(), RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("secret-resource-id")
                .hasMessageNotContaining("批注正文");
    }

    /** 创建同时触发图片占位和批注尾注的文档。 */
    private static DocumentModel documentWithImageAndAnnotation() {
        return new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new ParagraphNode("body", List.of(new TextNode("正文"))),
                        new ImageNode(
                                "image", "secret-resource-id", "图片替代文字", 40_000, 20_000),
                        new AnnotationNode(
                                AnnotationType.FREE_TEXT,
                                "body",
                                AnnotationPlacement.TOP_RIGHT,
                                30_000,
                                20_000,
                                1_000,
                                2_000,
                                "审核人",
                                "批注正文")));
    }

    /** @return 使用默认兼容策略的渲染器 */
    private static DocxDocumentRenderer compatibleRenderer() {
        return new DocxDocumentRenderer(DocxRendererOptions.defaults());
    }
}
