package io.github.leylaragg.letool.print.pdf.internal.layout;

import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 布局快照对重复区域边界的既有校验。 */
class PdfLayoutSnapshotTest {

    /** 页面区域高度恰好落在边距内可用，首次越界立即失败。 */
    @Test
    void shouldRejectPageRegionOverflow() {
        PdfLayoutSnapshot withinMargin = snapshot(56F, 56F);
        PdfLayoutSnapshot overflow = snapshot(57F, 56F);

        assertThatCode(() -> withinMargin.requireRegionsFit(PageLayout.a4Portrait()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> overflow.requireRegionsFit(PageLayout.a4Portrait()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("页眉")
                .hasMessageNotContaining("57");
    }

    /** 创建只带区域测量值的布局快照。 */
    private static PdfLayoutSnapshot snapshot(float headerPoints, float footerPoints) {
        PdfLayoutSnapshot.Position position = new PdfLayoutSnapshot.Position(
                0, new PDRectangle(0, 0, 1, 1));
        return new PdfLayoutSnapshot(Map.of("target", position), Map.of(),
                headerPoints, footerPoints);
    }
}
