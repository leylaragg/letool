package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 书签树的跨节点验证，独立于输出格式。 */
class BookmarkTreeDocumentTest {

    @Test
    void shouldAllowChildBeforeParentAndTwoTreesSharingATarget() {
        List<ParagraphNode> body = List.of(
                new ParagraphNode("form", List.of(new TextNode("Body"))),
                new ParagraphNode("", List.of(
                        bookmark("visit-child", "Same", "form", "by-visit"),
                        bookmark("form-child", "Same", "form", "by-form"),
                        bookmark("by-form", "By Form", "form", ""),
                        bookmark("by-visit", "By Visit", "form", ""))));
        assertThatCode(() -> document(body)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingTargetsMissingBookmarkParentsAndCycles() {
        assertInvalid(bookmark("entry", "Entry", "missing", ""));
        assertInvalid(bookmark("entry", "Entry", "form", "missing"));
        assertInvalid(bookmark("entry", "Entry", "form", "form"));
        assertThatThrownBy(() -> document(List.of(
                new ParagraphNode("form", List.of(new TextNode("Body"))),
                new ParagraphNode("", List.of(
                        bookmark("first", "First", "form", "second"),
                        bookmark("second", "Second", "form", "first"))))))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("环");
    }

    @Test
    void shouldResolveMetadataReferencesToContentAndRejectTargetCycles() {
        assertThatCode(() -> document(List.of(
                new ParagraphNode("form", List.of(new TextNode("Body"),
                        bookmark("first", "First", "form", ""),
                        bookmark("second", "Second", "first", "")))))).doesNotThrowAnyException();
        assertThatThrownBy(() -> document(List.of(new ParagraphNode("form", List.of(
                bookmark("first", "First", "second", ""),
                bookmark("second", "Second", "first", ""))))))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("目标引用形成环");
    }

    @Test
    void shouldAllowEmptyBookmarkContentsWhileKeepingHeadingContentsValidation() {
        TableOfContentsNode toc = bookmarkContents("Contents", "by-visit");
        assertThatCode(() -> document(List.of(toc,
                new ParagraphNode("contents", List.of(new TextNode("Empty"),
                        bookmark("by-visit", "By Visit", "contents", ""))))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> document(List.of(new TableOfContentsNode("Contents", 1, 3))))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("标题");
        assertThatThrownBy(() -> document(List.of(bookmarkContents("Contents", "missing"),
                new ParagraphNode("form", List.of(new TextNode("Body"))))))
                .isInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldAcceptMoreThan256SequencesButRetainAnAbsoluteLimit() {
        List<PageSequence> sequences = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of()));
        }
        assertThatCode(() -> new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences))
                .doesNotThrowAnyException();
        for (int i = 300; i < 20_001; i++) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of()));
        }
        assertThatThrownBy(() -> new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences))
                .isInstanceOf(PrintValidationException.class);
    }

    private void assertInvalid(BookmarkNode bookmark) {
        assertThatThrownBy(() -> document(List.of(
                new ParagraphNode("form", List.of(new TextNode("Body"), bookmark)))))
                .isInstanceOf(PrintValidationException.class);
    }

    private DocumentModel document(List<? extends io.github.leylaragg.letool.print.document.node.BlockNode> body) {
        return new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(),
                List.of(PageSequence.body(PageLayout.a4Portrait(), new ArrayList<>(body))));
    }

    private BookmarkNode bookmark(String id, String label, String target, String parent) {
        return new BookmarkNode(id, label, target, parent, true);
    }

    private TableOfContentsNode bookmarkContents(String title, String root) {
        return new TableOfContentsNode(title, 1, 3, root);
    }
}
