package io.github.leylaragg.letool.print.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 字体定义的输入边界测试。
 *
 * @author leyland
 */
class PdfFontTest {

    /** 字体元数据在构造时固定，每次渲染都能取得新的字体流。 */
    @Test
    void shouldKeepFontMetadataAndOpenIndependentStreams() throws Exception {
        PdfFont font = new PdfFont(
                "Noto Sans CJK",
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                true);

        assertThat(font.familyName()).isEqualTo("Noto Sans CJK");
        assertThat(font.fallback()).isTrue();
        try (var first = font.openStream(); var second = font.openStream()) {
            assertThat(first).isNotSameAs(second);
            assertThat(first.readAllBytes()).containsExactly(1, 2, 3);
            assertThat(second.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    /** 字体族只能是可安全写入框架 CSS 的普通名称。 */
    @Test
    void shouldRejectUnsafeFontFamily() {
        assertThatThrownBy(() -> new PdfFont(
                "Broken'; } body { color: red",
                () -> new ByteArrayInputStream(new byte[]{1}),
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字体族");
    }

    /** 字体供应器不能用空值掩盖配置错误。 */
    @Test
    void shouldRejectMissingFontStream() {
        PdfFont font = new PdfFont("Noto Sans", () -> null, false);

        assertThatThrownBy(font::openStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("字体流");
    }
}
