package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintRenderingException;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.relationships.Relationship;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 DOCX 容量限制和 OOXML 包边界。
 *
 * @author leyland
 */
class DocxRendererSecurityTest {

    /** 外部关系即使由可信 Java 代码误加，也不能进入最终产物。 */
    @Test
    void shouldRejectExternalRelationshipDuringPackageValidation() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        Relationship relationship = new Relationship();
        relationship.setId("rIdExternal");
        relationship.setType(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink");
        relationship.setTarget("https://example.invalid/secret");
        relationship.setTargetMode("External");
        wordPackage.getMainDocumentPart().getRelationshipsPart()
                .addRelationship(relationship);

        assertThatThrownBy(() -> new DocxPackageValidator().validate(wordPackage))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("example.invalid");
    }

    /** 正常产物不应携带外部、宏或嵌入对象部件。 */
    @Test
    void shouldContainNoExternalOrExecutableParts() throws Exception {
        byte[] content = renderer().render(
                document("安全正文"), RenderOptions.defaults()).content();
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(content));

        assertThat(reopened.getExternalResources()).isEmpty();
        assertThat(zipEntryNames(content)).noneMatch(this::isExecutableOrEmbeddedPart);
    }

    /** 写出超过通用容量限制时应转换为稳定的打印异常。 */
    @Test
    void shouldRejectOutputBeforeGrowingPastConfiguredLimit() {
        StringBuilder text = new StringBuilder(2_000_000);
        long state = 0x5DEECE66DL;
        for (int index = 0; index < 2_000_000; index++) {
            state = state * 2_862_933_555_777_941_757L + 3_037_000_493L;
            text.append((char) ('!' + Math.floorMod(state, 90)));
        }
        RenderOptions oneMiB = new RenderOptions(1, 1024L * 1024, false);

        assertThatThrownBy(() -> renderer().render(document(text.toString()), oneMiB))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_007")
                .hasMessageNotContaining(text.substring(0, 64));
    }

    /** 创建只有一个正文段落的文档。 */
    private static DocumentModel document(String text) {
        return new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new ParagraphNode("", List.of(new TextNode(text)))));
    }

    /** @return 默认 DOCX 渲染器 */
    private static DocxDocumentRenderer renderer() {
        return new DocxDocumentRenderer(DocxRendererOptions.defaults());
    }

    /** 读取 ZIP 中的全部部件名称。 */
    private static List<String> zipEntryNames(byte[] content) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    /** 判断部件名是否指向本阶段禁止的可执行或嵌入内容。 */
    private boolean isExecutableOrEmbeddedPart(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("vbaproject")
                || normalized.contains("activex")
                || normalized.contains("embeddings")
                || normalized.contains("oleobject")
                || normalized.contains("altchunk");
    }
}
