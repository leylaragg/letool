package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 字体目录的唯一性和查找语义测试。
 *
 * @author leyland
 */
class PdfFontCatalogTest {

    /** 同一字体族可以提供多个字重，并共同组成唯一回退族。 */
    @Test
    void shouldResolveFacesByFamilyAndWeight() {
        PdfFont normal = font("Droid Sans", FontWeight.NORMAL, true);
        PdfFont bold = font("Droid Sans", FontWeight.BOLD, true);

        PdfFontCatalog catalog = PdfFontCatalog.of(List.of(normal, bold));

        assertThat(catalog.requireFace("Droid Sans", FontWeight.NORMAL)).isSameAs(normal);
        assertThat(catalog.requireFace("Droid Sans", FontWeight.BOLD)).isSameAs(bold);
        assertThat(catalog.defaultFace(FontWeight.BOLD)).containsSame(bold);
        assertThat(catalog.fonts()).containsExactly(normal, bold);
    }

    /** 相同字体面会改变解析结果，目录必须在渲染前拒绝重复。 */
    @Test
    void shouldRejectDuplicateFamilyAndWeight() {
        PdfFont first = font("Droid Sans", FontWeight.NORMAL, true);
        PdfFont duplicate = font("Droid Sans", FontWeight.NORMAL, true);

        assertThatThrownBy(() -> PdfFontCatalog.of(List.of(first, duplicate)))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("字体面重复");
    }

    /** 回退链只能有一个字体族，避免不同节点得到不一致的默认字体。 */
    @Test
    void shouldRejectDifferentFallbackFamiliesAndMissingFace() {
        assertThatThrownBy(() -> PdfFontCatalog.of(List.of(
                font("First", FontWeight.NORMAL, true),
                font("Second", FontWeight.NORMAL, true))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("回退字体族");

        PdfFontCatalog catalog = PdfFontCatalog.of(List.of(
                font("Droid Sans", FontWeight.NORMAL, false)));
        assertThatThrownBy(() -> catalog.requireFace("Droid Sans", FontWeight.BOLD))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("字体面不存在");
    }

    /** 创建不依赖真实文件的字体面配置。 */
    private PdfFont font(String family, FontWeight weight, boolean fallback) {
        return new PdfFont(family, weight,
                () -> new ByteArrayInputStream(new byte[]{1}), fallback);
    }
}
