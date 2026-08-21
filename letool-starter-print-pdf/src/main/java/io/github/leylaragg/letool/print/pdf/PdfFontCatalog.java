package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 冻结宿主提供的 PDF 字体面，并按字体族和字重提供确定查找。
 *
 * @author leyland
 */
public final class PdfFontCatalog {

    /** 按宿主注册顺序保存的字体面。 */
    private final List<PdfFont> fonts;

    /** 用字体族和字重定位唯一字体面。 */
    private final Map<FontFaceKey, PdfFont> faces;

    /** 唯一回退字体族，没有配置时为空。 */
    private final String fallbackFamily;

    /** 保存已经检查过唯一性的字体快照。 */
    private PdfFontCatalog(
            List<PdfFont> fonts,
            Map<FontFaceKey, PdfFont> faces,
            String fallbackFamily) {
        this.fonts = fonts;
        this.faces = faces;
        this.fallbackFamily = fallbackFamily;
    }

    /**
     * 创建不可变字体目录。
     *
     * @param fonts 宿主按优先级提供的字体面
     * @return 可并发复用的字体目录
     */
    public static PdfFontCatalog of(List<PdfFont> fonts) {
        Objects.requireNonNull(fonts, "fonts 不能为空");
        List<PdfFont> snapshot = List.copyOf(fonts);
        Map<FontFaceKey, PdfFont> faces = new LinkedHashMap<>();
        String fallbackFamily = null;
        for (PdfFont font : snapshot) {
            FontFaceKey key = new FontFaceKey(font.familyName(), font.weight());
            if (faces.putIfAbsent(key, font) != null) {
                throw PrintValidationException.invalidDocument("PDF 字体面重复");
            }
            if (!font.fallbackFamily()) {
                continue;
            }
            if (fallbackFamily != null && !fallbackFamily.equals(font.familyName())) {
                throw PrintValidationException.invalidDocument("PDF 只能配置一个回退字体族");
            }
            fallbackFamily = font.familyName();
        }
        return new PdfFontCatalog(snapshot, Map.copyOf(faces), fallbackFamily);
    }

    /**
     * 查找模型明确指定的字体面，不用其他字重代替。
     *
     * @param familyName 字体族名称
     * @param weight 需要的字重
     * @return 完全匹配的字体面
     */
    public PdfFont requireFace(String familyName, FontWeight weight) {
        Objects.requireNonNull(familyName, "familyName 不能为空");
        Objects.requireNonNull(weight, "weight 不能为空");
        PdfFont font = faces.get(new FontFaceKey(familyName.trim(), weight));
        if (font == null) {
            throw PrintValidationException.invalidDocument("PDF 字体面不存在");
        }
        return font;
    }

    /**
     * 按字重查找默认字体面。
     *
     * @param weight 需要的字重
     * @return 回退字体族中完全匹配的字体面
     */
    public Optional<PdfFont> defaultFace(FontWeight weight) {
        Objects.requireNonNull(weight, "weight 不能为空");
        if (fallbackFamily == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(faces.get(new FontFaceKey(fallbackFamily, weight)));
    }

    /** @return 保持宿主注册顺序的字体面快照 */
    public List<PdfFont> fonts() {
        return fonts;
    }

    /** 字体目录内部使用的唯一键。 */
    private static final class FontFaceKey {

        /** 字体族名称。 */
        private final String familyName;

        /** 字体面字重。 */
        private final FontWeight weight;

        /** 保存一组已经规范化的字体面身份。 */
        private FontFaceKey(String familyName, FontWeight weight) {
            this.familyName = familyName;
            this.weight = weight;
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof FontFaceKey other)) {
                return false;
            }
            return familyName.equals(other.familyName) && weight == other.weight;
        }

        @Override
        public int hashCode() {
            return Objects.hash(familyName, weight);
        }
    }
}
