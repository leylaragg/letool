package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.AnnotationPlacement;
import com.github.leyland.letool.print.document.node.AnnotationType;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证同一 DOCX 渲染器实例不会在并发请求间共享包和计数状态。
 *
 * @author leyland
 */
class DocxRendererConcurrencyTest {

    /** 目录、表格和降级节点并发出现时，每份产物仍应只包含自己的内容。 */
    @Test
    void shouldIsolateConcurrentRenderingState() throws Exception {
        DocxDocumentRenderer renderer = new DocxDocumentRenderer(DocxRendererOptions.defaults());
        List<Callable<RenderedDocument>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            int current = index;
            tasks.add(() -> renderer.render(document(current), RenderOptions.defaults()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var futures = executor.invokeAll(tasks);
            for (int index = 0; index < futures.size(); index++) {
                RenderedDocument rendered = futures.get(index).get();
                WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                        new ByteArrayInputStream(rendered.content()));
                String mainXml = XmlUtils.marshaltoString(
                        reopened.getMainDocumentPart().getJaxbElement(), true, true);

                assertThat(mainXml).contains("正文-" + index, "表格-" + index);
                assertThat(rendered.metadata())
                        .containsEntry("degradedNodeCount", "2")
                        .containsEntry("degradedNodeTypes", "annotation,image")
                        .containsEntry("fieldUpdateRequired", "true");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** 创建同时覆盖导航、表格和兼容降级的并发样本文档。 */
    private static DocumentModel document(int index) {
        String targetId = "body-" + index;
        TableNode table = new TableNode("", 0, List.of(new TableRow(List.of(
                new TableCell(List.of(new ParagraphNode(
                        "", List.of(new TextNode("表格-" + index)))), 1, 1)))));
        return new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new TableOfContentsNode("目录", 1, 1),
                        new HeadingNode("", 1, List.of(new TextNode("标题-" + index))),
                        new ParagraphNode(targetId, List.of(new TextNode("正文-" + index))),
                        table,
                        new ImageNode("", "resource-" + index, "图片-" + index, 20_000, 10_000),
                        new AnnotationNode(
                                AnnotationType.TEXT_NOTE,
                                targetId,
                                AnnotationPlacement.TOP_LEFT,
                                10_000,
                                10_000,
                                0,
                                0,
                                "作者-" + index,
                                "批注-" + index)));
    }
}
