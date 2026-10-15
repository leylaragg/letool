package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 受控页面循环与动态导航路径的契约回归。 */
class XmlDynamicPageNavigationTest {

    @Test
    void shouldBindEachPageRegionInTheSameOccurrenceScope() {
        DocumentModel document = bind(document("""
                <page><page-body><paragraph>Cover</paragraph></page-body></page>
                <page items="occurrences" var="occurrence">
                    <page-header><paragraph><field path="$occurrence.header"/></paragraph></page-header>
                    <page-body><paragraph id-path="$occurrence.id"><field
                        path="$occurrence.body"/></paragraph></page-body>
                    <page-footer><paragraph><field path="$occurrence.footer"/></paragraph></page-footer>
                </page>
                """), data());

        assertThat(document.pageSequences()).hasSize(3);
        assertThat(text(document.pageSequences().get(1))).containsExactly("H1", "B1", "F1");
        assertThat(text(document.pageSequences().get(2))).containsExactly("H2", "B2", "F2");
        assertThat(document.pageSequences().get(1).body().get(0).id()).isEqualTo("form-1");
        assertThat(document.pageSequences().get(2).body().get(0).id()).isEqualTo("form-2");
    }

    @Test
    void shouldAllowZeroExpandedPagesBesideStaticPages() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putArray("occurrences");
        DocumentModel document = bind(document("""
                <page><page-body><paragraph>Cover</paragraph></page-body></page>
                <page items="occurrences" var="occurrence"><page-body>
                    <paragraph><field path="$occurrence.body"/></paragraph>
                </page-body></page>
                """), root);
        assertThat(document.pageSequences()).hasSize(1);
    }

    @Test
    void shouldBindDynamicIdsLabelsAndLinkTargets() {
        DocumentModel document = bind(document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <section id-path="$occurrence.id"><paragraph>
                        <bookmark id-path="$occurrence.bookmark" label-path="$occurrence.body"/>
                        <link target-path="$occurrence.id">Open</link>
                    </paragraph></section>
                </page-body></page>
                """), data());
        assertThat(DocumentTraversal.depthFirst(document).stream()
                .filter(BookmarkNode.class::isInstance).map(BookmarkNode.class::cast))
                .extracting(BookmarkNode::id).containsExactly("bookmark-1", "bookmark-2");
        assertThat(DocumentTraversal.depthFirst(document).stream()
                .filter(InternalLinkNode.class::isInstance).map(InternalLinkNode.class::cast))
                .extracting(InternalLinkNode::targetId).containsExactly("form-1", "form-2");
    }

    @Test
    void shouldInspectPageLoopAndEveryDynamicAttributeWithItsScope() {
        CompiledXmlTemplate template = compile(document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <paragraph id-path="$occurrence.id">
                        <bookmark id-path="$occurrence.bookmark" label-path="$occurrence.body"
                                  target-path="$occurrence.id" parent-path="$occurrence.parent"/>
                        <link target-path="$occurrence.id">Open</link>
                    </paragraph>
                </page-body></page>
                """));
        assertThat(template.inspection().features()).contains(DocumentFeature.MULTIPLE_PAGE_SEQUENCES);
        assertThat(template.inspection().pathUsages()).extracting(usage -> usage.dataPath())
                .containsExactly("occurrences", "$occurrence.id", "$occurrence.bookmark",
                        "$occurrence.body", "$occurrence.id", "$occurrence.parent", "$occurrence.id");
        assertThat(template.inspection().pathUsages()).first().satisfies(usage -> {
            assertThat(usage.visibleVariables()).isEmpty();
            assertThat(usage.location().tagPath()).isEqualTo("/document/page[1]");
        });
        assertThat(template.inspection().pathUsages()).allSatisfy(usage -> {
            if (usage.dataPath().startsWith("$")) {
                assertThat(usage.visibleVariables()).containsExactly("occurrence");
            }
        });
    }

    @Test
    void shouldBindIdsOnHeadingTableAndImage() {
        String xml = document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <heading id-path="$occurrence.heading">Heading</heading>
                    <table id-path="$occurrence.table"><body><row><cell>
                        <paragraph>Cell</paragraph></cell></row></body></table>
                    <image id-path="$occurrence.image" resource-id="logo" alt="Logo" width="10mm" height="10mm"/>
                </page-body></page>
                """);
        ObjectNode root = data();
        for (int i = 0; i < 2; i++) {
            ((ObjectNode) root.path("occurrences").get(i)).put("heading", "heading-" + i)
                    .put("table", "table-" + i).put("image", "image-" + i);
        }
        assertThat(DocumentTraversal.depthFirst(bind(xml, root)).stream()
                .map(io.github.leylaragg.letool.print.document.node.DocumentNode::id).filter(id -> !id.isEmpty()))
                .containsExactly("heading-0", "table-0", "image-0", "heading-1", "table-1", "image-1");
    }

    @Test
    void shouldRejectInvalidDynamicPagesAndPaths() {
        for (String attrs : List.of("items=\"occurrences\"", "var=\"occurrence\"",
                "items=\"occurrences[0]\" var=\"occurrence\"")) {
            assertThatThrownBy(() -> compile(document("<page " + attrs + "><page-body/></page>")))
                    .isInstanceOf(PrintCompilationException.class);
        }
        assertThatThrownBy(() -> compile(document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <paragraph id-path="$unknown.id">Bad</paragraph>
                </page-body></page>
                """))).isInstanceOf(PrintCompilationException.class).hasMessageContaining("未声明变量");
        for (String value : List.of("missing", "scalar")) {
            ObjectNode root = JsonNodeFactory.instance.objectNode().put("scalar", "invalid");
            assertThatThrownBy(() -> bind(document("<page items=\"" + value
                    + "\" var=\"item\"><page-body/></page>"), root))
                    .isInstanceOf(PrintValidationException.class);
        }
    }

    @Test
    void shouldRejectDuplicateDynamicIdsAndInvalidScalarIds() {
        String xml = document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <paragraph id-path="$occurrence.id">Body</paragraph>
                </page-body></page>
                """);
        ObjectNode duplicate = data();
        ((ObjectNode) duplicate.path("occurrences").get(1)).put("id", "form-1");
        assertThatThrownBy(() -> bind(xml, duplicate)).isInstanceOf(PrintValidationException.class)
                .hasCauseInstanceOf(PrintValidationException.class)
                .satisfies(failure -> assertThat(failure.getCause().getMessage()).contains("ID 重复"));
        for (String value : List.of("", "1-invalid", "with space")) {
            ObjectNode invalid = data();
            ((ObjectNode) invalid.path("occurrences").get(0)).put("id", value);
            assertThatThrownBy(() -> bind(xml, invalid)).isInstanceOf(PrintValidationException.class);
        }
        ObjectNode object = data();
        ((ObjectNode) object.path("occurrences").get(0)).putObject("id");
        assertThatThrownBy(() -> bind(xml, object)).isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("标量");
    }

    @Test
    void shouldKeepLegacyBookmarksAndDefaultNewGroupsToCollapsed() {
        DocumentModel document = bind(document("""
                <page><page-body><paragraph id="cover">Cover
                    <bookmark id="legacy" label="Legacy"/>
                    <bookmark id="group" label="Group" target="cover"/>
                    <bookmark id="child" label="Child" target="cover" parent="group"/>
                </paragraph></page-body></page>
                """), JsonNodeFactory.instance.objectNode());
        List<BookmarkNode> bookmarks = DocumentTraversal.depthFirst(document).stream().filter(BookmarkNode
            .class::isInstance)
                .map(BookmarkNode.class::cast).toList();
        assertThat(bookmarks.get(0).targetId()).isEmpty();
        assertThat(bookmarks.get(0).collapsed()).isFalse();
        assertThat(bookmarks.get(1).targetId()).isEqualTo("cover");
        assertThat(bookmarks.get(1).collapsed()).isTrue();
        assertThat(bookmarks.get(2).parentId()).isEqualTo("group");
    }

    @Test
    void shouldBindDocumentNavigationAndValidateInitialTarget() {
        String xml = "<document xmlns=\"https://leyland.github.io/letool/print/v1\" context-version=\"1\""
                + " destination-mode=\"page-top\" initial-target=\"cover\" show-bookmarks=\"true\">"
                + "<page><page-body><paragraph id=\"cover\">Cover</paragraph></page-body></page></document>";
        DocumentModel document = bind(xml, JsonNodeFactory.instance.objectNode());
        io.github.leylaragg.letool.print.document.DocumentNavigationOptions navigation = document.options()
            .navigation();
        assertThat(navigation.destinationMode().toString())
                .isEqualTo("PAGE_TOP");
        assertThat(navigation.initialTarget()).isEqualTo("cover");
        assertThat(navigation.showBookmarks()).isTrue();
        assertThatThrownBy(() -> bind(xml.replace("initial-target=\"cover\"", "initial-target=\"missing\""),
                JsonNodeFactory.instance.objectNode())).isInstanceOf(PrintValidationException.class);
        for (String replacement : List.of("destination-mode=\"unknown\"", "show-bookmarks=\"yes\"")) {
            String invalid = replacement.startsWith("destination")
                    ? xml.replace("destination-mode=\"page-top\"", replacement)
                    : xml.replace("show-bookmarks=\"true\"", replacement);
            assertThatThrownBy(() -> compile(invalid)).isInstanceOf(PrintCompilationException.class);
        }
    }

    @Test
    void shouldRejectStaticIdsInPageLoopsAndConflictingAttributePairs() {
        assertThatThrownBy(() -> compile(document("""
                <page items="occurrences" var="occurrence"><page-body>
                    <paragraph id="static">Body</paragraph>
                </page-body></page>
                """))).isInstanceOf(PrintCompilationException.class).hasMessageContaining("静态 ID");
        for (String content : List.of(
                "<paragraph id=\"static\" id-path=\"id\">Body</paragraph>",
                "<paragraph><bookmark id=\"book\" label=\"Label\" label-path=\"label\"/></paragraph>",
                "<paragraph><link target=\"static\" target-path=\"id\">Open</link></paragraph>")) {
            assertThatThrownBy(() -> compile(document("<page><page-body>" + content
                    + "</page-body></page>"))).isInstanceOf(PrintCompilationException.class);
        }
    }

    private List<String> text(io.github.leylaragg.letool.print.document.PageSequence sequence) {
        java.util.ArrayList<io.github.leylaragg.letool.print.document.node.BlockNode> blocks = new java.util
            .ArrayList<>(sequence.header().blocks());
        blocks.addAll(sequence.body());
        blocks.addAll(sequence.footer().blocks());
        return DocumentTraversal.depthFirstBlocks(blocks).stream().filter(TextNode.class::isInstance)
                .map(TextNode.class::cast).map(TextNode::text).toList();
    }

    private ObjectNode data() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        com.fasterxml.jackson.databind.node.ArrayNode items = root.putArray("occurrences");
        for (int i = 1; i <= 2; i++) {
            items.addObject().put("id", "form-" + i).put("bookmark", "bookmark-" + i)
                    .put("header", "H" + i).put("body", "B" + i).put("footer", "F" + i);
        }
        return root;
    }

    private String document(String pages) {
        return "<document xmlns=\"https://leyland.github.io/letool/print/v1\" context-version=\"1\">"
                + pages + "</document>";
    }

    private CompiledXmlTemplate compile(String xml) {
        return new XmlTemplateCompiler().compile(new PrintTemplate("dynamic-pages",
                TemplateFormat.LETOOL_XML, 1, 1, 1, xml.getBytes(StandardCharsets.UTF_8)));
    }

    private DocumentModel bind(String xml, ObjectNode data) {
        return new XmlTemplateBinder().bind(compile(xml), PrintContext.of(1, data));
    }
}
