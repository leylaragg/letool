package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 页面序列和逻辑页码的基础契约测试。
 *
 * @author leyland
 */
class PageSequenceTest {

    /** 验证三种页码规则不会暴露含义不清的字段组合。 */
    @Test
    void shouldCreateExplicitPageNumberingRules() {
        assertThat(PageNumbering.counted().includedInCount()).isTrue();
        assertThat(PageNumbering.counted().restartAt()).isEmpty();
        assertThat(PageNumbering.countedFrom(5).restartAt()).hasValue(5);
        assertThat(PageNumbering.excluded().includedInCount()).isFalse();

        assertThatThrownBy(() -> PageNumbering.countedFrom(0))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 验证简单页面工厂和页眉页脚都与调用方可变集合隔离。 */
    @Test
    void shouldBuildImmutablePageSequence() {
        List<BlockNode> body = new ArrayList<>();
        body.add(paragraph("正文"));
        List<BlockNode> header = new ArrayList<>();
        header.add(paragraph("页眉"));

        PageSequence sequence = new PageSequence(
                PageLayout.a4Portrait(),
                new PageRegion(header),
                PageRegion.empty(),
                PageNumbering.counted(),
                body);
        body.clear();
        header.clear();

        assertThat(sequence.body()).hasSize(1);
        assertThat(sequence.header().blocks()).hasSize(1);
        assertThat(sequence.footer().blocks()).isEmpty();
        assertThatThrownBy(() -> sequence.body().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(PageSequence.body(PageLayout.a4Portrait(), List.of()).body()).isEmpty();
    }

    /** 创建不带样式的普通段落。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", "", List.of(new TextNode(text, "")));
    }
}
