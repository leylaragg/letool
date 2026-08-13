package com.github.leyland.letool.print.document;

import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通用文档模型的不变量和树级约束测试。
 *
 * @author leyland
 */
class DocumentModelTest {

    /** 验证可演进文档结构不把 record 作为默认建模方式。 */
    @Test
    void shouldPreferRegularClassesForEvolvingDocumentStructure() {
        assertThat(List.of(
                PageLayout.class,
                HeadingNode.class,
                ParagraphNode.class,
                SectionNode.class,
                TableNode.class,
                TableRow.class,
                TableCell.class,
                ImageNode.class,
                BookmarkNode.class,
                InternalLinkNode.class))
                .allMatch(type -> !type.isRecord());
    }

    /** 验证文档根节点与调用方可变集合隔离。 */
    @Test
    void shouldBuildImmutableDocumentTree() {
        List<BlockNode> blocks = new ArrayList<>();
        blocks.add(new HeadingNode("title", 1, List.of(new TextNode("合同标题"))));

        DocumentModel model = new DocumentModel(
                new DocumentMetadata("合同", "Letool", "zh-CN"),
                PageLayout.a4Portrait(),
                blocks);
        blocks.clear();

        assertThat(model.blocks()).hasSize(1);
        assertThatThrownBy(() -> model.blocks().add(PageBreakNode.INSTANCE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证节点级结构边界。 */
    @Test
    void shouldRejectInvalidHeadingAndTableSpan() {
        assertThatThrownBy(() -> new HeadingNode("h", 0, List.of(new TextNode("x"))))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new TableCell(List.of(), 0, 1))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 验证整棵文档树拒绝重复逻辑 ID。 */
    @Test
    void shouldRejectDuplicateDocumentNodeIds() {
        DocumentModel model = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("same", 1, List.of(new TextNode("A"))),
                        new ParagraphNode("same", List.of(new TextNode("B")))));

        assertThatThrownBy(model::validate)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("same");
    }

    /** 验证内部链接目标必须真实存在，并且遍历包含链接标签。 */
    @Test
    void shouldValidateInternalLinkTargetsAndTraversalOrder() {
        DocumentModel invalid = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new ParagraphNode(
                        "paragraph",
                        List.of(new InternalLinkNode("missing", List.of(new TextNode("跳转")))))));

        assertThatThrownBy(invalid::validate)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
        assertThat(DocumentTraversal.depthFirst(invalid))
                .extracting(node -> node.getClass().getSimpleName())
                .containsExactly("ParagraphNode", "InternalLinkNode", "TextNode");
    }

    /** 验证默认页面使用稳定微米单位和 20 mm 页边距。 */
    @Test
    void shouldUseStablePhysicalPageUnits() {
        PageLayout layout = PageLayout.a4Portrait();

        assertThat(layout.pageSize()).isEqualTo(PageSize.A4);
        assertThat(layout.orientation()).isEqualTo(PageOrientation.PORTRAIT);
        assertThat(layout.margins()).isEqualTo(new Margins(20_000, 20_000, 20_000, 20_000));
    }
}
