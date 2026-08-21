package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分段 PDF 使用的稳定布局 ID 与中央导航标记测试。
 *
 * @author leyland
 */
class PdfNavigationWriterTest {

    /** 自动标题 ID 可重复生成，且会跳过用户已经占用的保留前缀。 */
    @Test
    void shouldCreateStableNonConflictingHeadingIds() {
        HeadingNode explicit = heading("letool-toc-heading-1", "显式");
        HeadingNode automatic = heading("", "自动");
        DocumentModel document = document(List.of(explicit, automatic));

        PdfRenderIds first = PdfRenderIds.create(document);
        PdfRenderIds second = PdfRenderIds.create(document);

        assertThat(first.targetId(explicit)).isEqualTo("letool-toc-heading-1");
        assertThat(first.targetId(automatic)).isEqualTo("letool-toc-heading-2");
        assertThat(second.targetId(automatic)).isEqualTo(first.targetId(automatic));
    }

    /** 值相同但位置不同的链接必须拥有各自的布局源 ID。 */
    @Test
    void shouldAssignLinkIdsByNodeIdentity() {
        InternalLinkNode firstLink = link("target");
        InternalLinkNode secondLink = link("target");
        DocumentModel document = document(List.of(
                heading("target", "目标"),
                new ParagraphNode("", List.of(firstLink, new TextNode(" / "), secondLink))));

        PdfRenderIds ids = PdfRenderIds.create(document);
        PdfDocumentPlan plan = PdfDocumentPlan.create(document);

        assertThat(ids.sourceId(firstLink)).isNotEqualTo(ids.sourceId(secondLink));
        String xhtml = new PdfXhtmlRenderer(PdfFontCatalog.of(List.of()))
                .render(plan, plan.sequences().get(0),
                        document.pageSequences().get(0).body(),
                        new PdfPaginationPlanner(5).initial(plan), ids, true);
        assertThat(xhtml).contains("class=\"internal-link\"")
                .contains("id=\"" + ids.sourceId(firstLink) + "\"")
                .doesNotContain("href=\"#target\"")
                .doesNotContain("<bookmarks>");
    }

    private static HeadingNode heading(String id, String text) {
        return new HeadingNode(id, 1, List.of(new TextNode(text)));
    }

    private static InternalLinkNode link(String target) {
        return new InternalLinkNode(target, List.of(new TextNode("跳转")));
    }

    private static DocumentModel document(List<BlockNode> blocks) {
        return DocumentModel.singleSequence(DocumentMetadata.empty(), PageLayout.a4Portrait(), blocks);
    }
}
