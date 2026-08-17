package com.github.leyland.letool.print.docx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 校验 DOCX 渲染选项的默认值和输入边界。
 *
 * @author leyland
 */
class DocxRendererOptionsTest {

    /** 默认选项应当适合普通中西文文档，并优先保证兼容输出。 */
    @Test
    void shouldProvideStableCompatibleDefaults() {
        DocxRendererOptions options = DocxRendererOptions.defaults();

        assertThat(options.compatibilityMode()).isEqualTo(DocxCompatibilityMode.COMPATIBLE);
        assertThat(options.westernFontFamily()).isEqualTo("Arial");
        assertThat(options.eastAsiaFontFamily()).isEqualTo("SimSun");
        assertThat(options.bodyFontSizeHalfPoints()).isEqualTo(21);
        assertThat(DocxRendererOptions.class.isRecord()).isFalse();
    }

    /** 构造时完成规范化，实例便不再依赖调用方后续状态。 */
    @Test
    void shouldNormalizeValidOptions() {
        DocxRendererOptions options = new DocxRendererOptions(
                DocxCompatibilityMode.STRICT, " Arial ", " 宋体 ", 24);

        assertThat(options.compatibilityMode()).isEqualTo(DocxCompatibilityMode.STRICT);
        assertThat(options.westernFontFamily()).isEqualTo("Arial");
        assertThat(options.eastAsiaFontFamily()).isEqualTo("宋体");
        assertThat(options.bodyFontSizeHalfPoints()).isEqualTo(24);
    }

    /** 字体名和字号超出公开边界时应尽早拒绝。 */
    @Test
    void shouldRejectUnsafeFontAndFontSize() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocxRendererOptions(DocxCompatibilityMode.COMPATIBLE, "\u0000", "宋体", 21));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocxRendererOptions(DocxCompatibilityMode.COMPATIBLE, "Arial", " ", 21));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocxRendererOptions(DocxCompatibilityMode.COMPATIBLE, "Arial", "宋体", 11));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocxRendererOptions(DocxCompatibilityMode.COMPATIBLE, "Arial", "宋体", 145));
    }
}
