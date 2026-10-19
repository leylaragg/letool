package io.github.leylaragg.letool.print.pdf.internal.render;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 文档计划对大量页面序列的既有容量行为。 */
class PdfDocumentPlanSequenceTest {

    @Test
    void shouldPlanMoreThan1000SequencesWithinTheAbsoluteBound() {
        List<PageSequence> sequences = new ArrayList<>();
        for (int i = 0; i < 1_200; i++) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(new ParagraphNode("", List.of(new TextNode("Body"))))));
        }
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences);
        assertThat(PdfDocumentPlan.create(document).sequences()).hasSize(1_200);
    }

}
