package io.github.leylaragg.letool.print.render;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 输出节点能力检查的契约测试。
 *
 * @author leyland
 */
class OutputCapabilityTest {

    /** 验证能力集合与调用方隔离，并复用完整文档遍历发现不支持节点。 */
    @Test
    void shouldRequireEveryDocumentNodeType() {
        Set<Class<? extends DocumentNode>> supported = new HashSet<>();
        supported.add(HeadingNode.class);
        OutputCapability capability = new OutputCapability(supported);
        supported.add(TextNode.class);
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                StyleSheet.empty(),
                List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                        new HeadingNode("heading", 1, "", List.of(new TextNode("标题", "")))))));

        assertThatThrownBy(() -> capability.requireSupports(document))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("TextNode");
    }

    /** 验证节点均受支持时，未声明的文档特性仍会被明确拒绝。 */
    @Test
    void shouldRequireDocumentFeatures() {
        Set<Class<? extends DocumentNode>> nodes = Set.of(ParagraphNode.class, TextNode.class);
        OutputCapability capability = new OutputCapability(nodes);
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(), StyleSheet.empty(), List.of(
                        PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("A"))),
                        PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("B")))));

        assertThatThrownBy(() -> capability.requireSupports(document))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("MULTIPLE_PAGE_SEQUENCES");

        new OutputCapability(nodes, Set.of(DocumentFeature.MULTIPLE_PAGE_SEQUENCES))
                .requireSupports(document);
    }

    /** 创建不带样式的普通段落。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", "", List.of(new TextNode(text, "")));
    }
}
