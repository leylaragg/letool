package io.github.leylaragg.letool.print.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证渲染选项的兼容构造器、批注及字体预算边界和值语义。
 *
 * @author leyland
 */
class RenderOptionsTest {

    @Test
    void shouldKeepDefaultAnnotationBudgetForLegacyConstructor() {
        RenderOptions legacy = new RenderOptions(2_500, 100L * 1024 * 1024, 300L * 1024 * 1024, true);
        RenderOptions explicit = new RenderOptions(2_500, 100L * 1024 * 1024, 300L * 1024 * 1024, true, 1_000);

        assertThat(RenderOptions.DEFAULT_MAX_ANNOTATIONS).isEqualTo(1_000);
        assertThat(RenderOptions.defaults().maxAnnotations()).isEqualTo(1_000);
        assertThat(legacy.maxAnnotations()).isEqualTo(1_000);
        assertThat(legacy).isEqualTo(explicit).isEqualTo(RenderOptions.defaults());
        assertThat(legacy.hashCode()).isEqualTo(explicit.hashCode());
    }

    @Test
    void shouldIncludeAnnotationBudgetInValueSemantics() {
        RenderOptions first = options(2_000);
        RenderOptions same = options(2_000);
        RenderOptions different = options(2_001);

        assertThat(first.maxAnnotations()).isEqualTo(2_000);
        assertThat(first).isEqualTo(same).isNotEqualTo(different);
        assertThat(first.hashCode()).isEqualTo(same.hashCode()).isNotEqualTo(different.hashCode());
        assertThat(first.toString()).contains("maxAnnotations=2000");
    }

    @Test
    void shouldAllowZeroAnnotationBudget() {
        assertThat(options(0).maxAnnotations()).isZero();
    }

    @Test
    void shouldRejectNegativeAnnotationBudget() {
        assertThatThrownBy(() -> options(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAnnotations");
    }

    @Test
    void shouldKeepDefaultFontCacheBudgetForExistingConstructors() {
        RenderOptions fourArguments = new RenderOptions(100, 10L * 1024 * 1024, 30L * 1024 * 1024, true);
        RenderOptions fiveArguments = options(1_000);

        assertThat(RenderOptions.DEFAULT_MAX_FONT_CACHE_BYTES).isEqualTo(64L * 1024 * 1024);
        assertThat(RenderOptions.defaults().maxFontCacheBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(fourArguments.maxFontCacheBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(fiveArguments.maxFontCacheBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(fourArguments).isEqualTo(fontCacheOptions(64L * 1024 * 1024));
    }

    @Test
    void shouldIncludeFontCacheBudgetInValueSemantics() {
        RenderOptions first = fontCacheOptions(1024);
        RenderOptions same = fontCacheOptions(1024);
        RenderOptions different = fontCacheOptions(2048);

        assertThat(first.maxFontCacheBytes()).isEqualTo(1024);
        assertThat(first).isEqualTo(same).isNotEqualTo(different);
        assertThat(first.hashCode()).isEqualTo(same.hashCode()).isNotEqualTo(different.hashCode());
        assertThat(first.toString()).contains("maxFontCacheBytes=1024");
    }

    @Test
    void shouldAllowDisabledAndMaximumFontCacheBudget() {
        assertThat(fontCacheOptions(0).maxFontCacheBytes()).isZero();
        assertThat(fontCacheOptions(8L * 1024 * 1024 * 1024).maxFontCacheBytes())
                .isEqualTo(8L * 1024 * 1024 * 1024);
    }

    @Test
    void shouldRejectFontCacheBudgetOutsideSupportedRange() {
        assertThatThrownBy(() -> fontCacheOptions(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxFontCacheBytes");
        assertThatThrownBy(() -> fontCacheOptions(8L * 1024 * 1024 * 1024 + 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxFontCacheBytes");
    }

    private RenderOptions fontCacheOptions(long maxFontCacheBytes) {
        return new RenderOptions(100, 10L * 1024 * 1024, 30L * 1024 * 1024, true, 1_000, maxFontCacheBytes);
    }

    private RenderOptions options(int maxAnnotations) {
        return new RenderOptions(100, 10L * 1024 * 1024, 30L * 1024 * 1024, true, maxAnnotations);
    }
}
