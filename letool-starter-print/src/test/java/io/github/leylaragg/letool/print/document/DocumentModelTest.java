package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.ImageNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
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
                AnnotationNode.class,
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

    /** 验证批注只保存跨输出稳定的定位语义和纯文本内容。 */
    @Test
    void shouldCreateValidatedAnnotationNode() {
        AnnotationNode annotation = new AnnotationNode(
                AnnotationType.TEXT_NOTE,
                "summary",
                AnnotationPlacement.TOP_RIGHT,
                6_000,
                6_000,
                1_000,
                -1_000,
                "审核人",
                "请复核这一段");

        assertThat(annotation.id()).isEmpty();
        assertThat(annotation.type()).isEqualTo(AnnotationType.TEXT_NOTE);
        assertThat(annotation.targetId()).isEqualTo("summary");
        assertThat(annotation.placement()).isEqualTo(AnnotationPlacement.TOP_RIGHT);
        assertThat(annotation.widthMicrometers()).isEqualTo(6_000);
        assertThat(annotation.heightMicrometers()).isEqualTo(6_000);
        assertThat(annotation.offsetXMicrometers()).isEqualTo(1_000);
        assertThat(annotation.offsetYMicrometers()).isEqualTo(-1_000);
        assertThat(annotation.author()).isEqualTo("审核人");
        assertThat(annotation.content()).isEqualTo("请复核这一段");

        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.TEXT_NOTE, "", AnnotationPlacement.TOP_RIGHT,
                6_000, 6_000, 0, 0, "", "正文"))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                0, 6_000, 0, 0, "", "正文"))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                6_000, 6_000, 2_000_001, 0, "", "正文"))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                6_000, 6_000, 0, 0, "", " "))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                500_001, 6_000, 0, 0, "", "正文"))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                6_000, 6_000, 0, 0, "审".repeat(129), "正文"))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new AnnotationNode(
                AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.TOP_LEFT,
                6_000, 6_000, 0, 0, "", "字".repeat(50_001)))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 验证目录节点只保存跨输出稳定的标题和层级范围。 */
    @Test
    void shouldCreateValidatedTableOfContentsNode() {
        TableOfContentsNode contents = new TableOfContentsNode("目录", 1, 3);

        assertThat(TableOfContentsNode.class.isRecord()).isFalse();
        assertThat(contents.id()).isEmpty();
        assertThat(contents.title()).isEqualTo("目录");
        assertThat(contents.minLevel()).isEqualTo(1);
        assertThat(contents.maxLevel()).isEqualTo(3);
        assertThat(new TableOfContentsNode(null, 2, 6).title()).isNull();

        assertThatThrownBy(() -> new TableOfContentsNode(" ", 1, 3))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new TableOfContentsNode("目".repeat(257), 1, 3))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new TableOfContentsNode(null, 0, 3))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new TableOfContentsNode(null, 1, 7))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> new TableOfContentsNode(null, 4, 3))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 验证目录唯一位于文档根部，并能找到声明位置之后的可见标题。 */
    @Test
    void shouldValidateTableOfContentsPlacementAndHeadings() {
        DocumentModel valid = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("preface", 1, List.of(new TextNode("目录前标题"))),
                        new TableOfContentsNode("目录", 1, 2),
                        new SectionNode("chapter", List.of(new HeadingNode(
                                "chapter-title", 2, List.of(new TextNode("第一章")))))));

        valid.validate();

        assertThatThrownBy(() -> new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(),
                List.of(
                        new TableOfContentsNode(null, 1, 3),
                        new TableOfContentsNode(null, 1, 3),
                        new HeadingNode("heading", 1, List.of(new TextNode("标题"))))).validate())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("目录");
        assertThatThrownBy(() -> new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(),
                List.of(new SectionNode("chapter", List.of(
                        new TableOfContentsNode(null, 1, 3),
                        new HeadingNode("heading", 1, List.of(new TextNode("标题"))))))).validate())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("目录");
        assertThatThrownBy(() -> new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("before", 1, List.of(new TextNode("目录前标题"))),
                        new TableOfContentsNode(null, 2, 3),
                        new HeadingNode("after", 1, List.of(new TextNode("层级不匹配"))))).validate())
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("可收录标题");
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

    /** 验证批注和内部链接一样只能引用真实存在的文档节点。 */
    @Test
    void shouldValidateAnnotationTarget() {
        DocumentModel invalid = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new AnnotationNode(
                        AnnotationType.TEXT_NOTE,
                        "missing",
                        AnnotationPlacement.TOP_RIGHT,
                        6_000,
                        6_000,
                        0,
                        0,
                        "审核人",
                        "请复核")));

        assertThatThrownBy(invalid::validate)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
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
