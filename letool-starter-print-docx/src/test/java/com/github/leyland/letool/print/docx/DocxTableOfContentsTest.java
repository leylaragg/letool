package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DOCX 使用可更新的 Word 域表达目录。
 *
 * @author leyland
 */
class DocxTableOfContentsTest {

    /** 目录域应保留层级范围，并提供打开文档前就可阅读的标题缓存。 */
    @Test
    void shouldWriteUpdatableTocForConfiguredHeadingLevels() throws Exception {
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new TableOfContentsNode("目录", 2, 4),
                        new HeadingNode("", 1, List.of(new TextNode("不收录标题"))),
                        new HeadingNode("", 2, List.of(new TextNode("第二级"))),
                        new HeadingNode("", 4, List.of(new TextNode("第四级")))));

        RenderedDocument rendered = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults());
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(rendered.content()));
        String mainXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getJaxbElement(), true, true);
        String settingsXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getDocumentSettingsPart().getJaxbElement(),
                true, true);

        assertThat(mainXml)
                .contains("TOC \\o \"2-4\"", "目录", "第二级", "第四级")
                .contains("w:fldChar", "w:anchor");
        assertThat(settingsXml).contains("w:updateFields");
        assertThat(rendered.metadata()).containsEntry("fieldUpdateRequired", "true");
    }

    /** 内容相同的无 ID 标题仍代表两个位置，目录应为它们保留独立锚点。 */
    @Test
    void shouldKeepDistinctAnchorsForEqualHeadingValues() throws Exception {
        HeadingNode first = new HeadingNode("", 1, List.of(new TextNode("重复标题")));
        HeadingNode second = new HeadingNode("", 1, List.of(new TextNode("重复标题")));
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new TableOfContentsNode("目录", 1, 1), first, second));

        byte[] content = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults()).content();
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(content));
        String mainXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getJaxbElement(), true, true);

        assertThat(mainXml).contains("letool_bookmark_1", "letool_bookmark_2");
    }
}
