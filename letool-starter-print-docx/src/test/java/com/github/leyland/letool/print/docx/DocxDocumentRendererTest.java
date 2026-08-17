package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.Margins;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.PageOrientation;
import com.github.leyland.letool.print.document.PageSize;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.STPageOrientation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DOCX 基础纵向链路产出可重新打开的 Word 文档。
 *
 * @author leyland
 */
class DocxDocumentRendererTest {

    /** 页面、样式、换行和分页应落到标准 WordprocessingML 结构中。 */
    @Test
    void shouldRenderEditableDocumentWithPageLayoutAndMetadata() throws Exception {
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("示例文档", "测试作者", "zh-CN"),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("title", 1, List.of(new TextNode("标题"))),
                        new ParagraphNode("body", List.of(new TextNode("第一行\n第二行"))),
                        PageBreakNode.INSTANCE,
                        new ParagraphNode("", List.of())));

        RenderedDocument rendered = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults());
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(rendered.content()));
        String mainXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getJaxbElement(), true, true);

        assertThat(rendered.outputFormat()).isEqualTo(OutputFormat.DOCX);
        assertThat(rendered.metadata())
                .containsEntry("compatibilityMode", "COMPATIBLE")
                .containsEntry("degradedNodeCount", "0")
                .containsEntry("degradedNodeTypes", "")
                .containsEntry("fieldUpdateRequired", "false")
                .containsKey("contentLength")
                .doesNotContainKey("pageCount");
        assertThat(mainXml)
                .contains("标题", "第一行", "第二行", "Heading1")
                .contains("w:br")
                .contains("w:type=\"page\"")
                .contains("w:pgSz", "w:pgMar");
        assertThat(reopened.getMainDocumentPart().getContent()).hasSize(4);
    }

    /** 横向布局应交换实际宽高，关闭元数据后也不能留下业务属性。 */
    @Test
    void shouldRenderLandscapeLayoutWithoutDocumentMetadata() throws Exception {
        PageLayout landscape = new PageLayout(
                PageSize.A4, PageOrientation.LANDSCAPE, new Margins(10_000, 20_000, 30_000, 40_000));
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("不应写入", "不应写入", "zh-CN"),
                landscape,
                List.of(new ParagraphNode("", List.of(new TextNode("横向正文")))));
        RenderOptions options = new RenderOptions(100, 10L * 1024 * 1024, false);

        RenderedDocument rendered = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, options);
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(rendered.content()));
        var section = reopened.getMainDocumentPart().getJaxbElement().getBody().getSectPr();
        var properties = reopened.getDocPropsCorePart().getJaxbElement();

        assertThat(section.getPgSz().getOrient()).isEqualTo(STPageOrientation.LANDSCAPE);
        assertThat(section.getPgSz().getW()).isGreaterThan(section.getPgSz().getH());
        assertThat(section.getPgMar().getTop()).isEqualTo(567);
        assertThat(section.getPgMar().getRight()).isEqualTo(1_134);
        assertThat(section.getPgMar().getBottom()).isEqualTo(1_701);
        assertThat(section.getPgMar().getLeft()).isEqualTo(2_268);
        assertThat(properties.getTitle()).isNull();
        assertThat(properties.getCreator()).isNull();
        assertThat(properties.getLanguage()).isNull();
    }
}
