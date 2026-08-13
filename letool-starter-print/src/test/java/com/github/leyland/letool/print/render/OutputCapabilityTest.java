package com.github.leyland.letool.print.render;

import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;
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
                PageLayout.a4Portrait(),
                List.of(new HeadingNode("heading", 1, List.of(new TextNode("标题")))));

        assertThatThrownBy(() -> capability.requireSupports(document))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("TextNode");
    }
}
