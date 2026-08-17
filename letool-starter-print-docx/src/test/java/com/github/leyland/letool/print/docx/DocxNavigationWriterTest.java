package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证逻辑 ID 不会直接成为 Word 书签名称。
 *
 * @author leyland
 */
class DocxNavigationWriterTest {

    /** 标题、块节点和显式书签应共享请求内安全名称映射。 */
    @Test
    void shouldCreateSafeBookmarkNamesAndInternalLinks() throws Exception {
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("", 1, List.of(new TextNode("自动标题目标"))),
                        new ParagraphNode("paragraph-target", List.of(new TextNode("段落目标"))),
                        new ParagraphNode("", List.of(
                                new BookmarkNode("target-with-dash", "显式目标"))),
                        new ParagraphNode("", List.of(new InternalLinkNode(
                                "target-with-dash", List.of(new TextNode("跳转")))))));

        byte[] content = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults()).content();
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(content));
        String mainXml = XmlUtils.marshaltoString(
                reopened.getMainDocumentPart().getJaxbElement(), true, true);

        assertThat(mainXml)
                .contains("w:bookmarkStart", "letool_bookmark_", "w:anchor", "跳转")
                .doesNotContain("paragraph-target", "target-with-dash");
    }
}
