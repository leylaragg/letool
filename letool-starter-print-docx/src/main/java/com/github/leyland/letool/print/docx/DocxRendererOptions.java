package com.github.leyland.letool.print.docx;

import java.util.Objects;

/**
 * DOCX 渲染器的宿主级不可变配置。
 *
 * <p>这些选项描述渲染能力和默认排版，不接收模板内的实现选择。</p>
 *
 * @author leyland
 */
public final class DocxRendererOptions {

    /** 默认西文字体。 */
    private static final String DEFAULT_WESTERN_FONT = "Arial";

    /** 默认东亚字体。 */
    private static final String DEFAULT_EAST_ASIA_FONT = "SimSun";

    /** 默认正文字号，单位为半磅。 */
    private static final int DEFAULT_BODY_FONT_SIZE = 21;

    /** 遇到非等价节点时采用的处理方式。 */
    private final DocxCompatibilityMode compatibilityMode;

    /** 正文和样式使用的西文字体。 */
    private final String westernFontFamily;

    /** 正文和样式使用的东亚字体。 */
    private final String eastAsiaFontFamily;

    /** 正文字号，单位为半磅。 */
    private final int bodyFontSizeHalfPoints;

    /**
     * 创建 DOCX 渲染配置。
     *
     * @param compatibilityMode 非等价节点的处理方式
     * @param westernFontFamily 西文字体名称
     * @param eastAsiaFontFamily 东亚字体名称
     * @param bodyFontSizeHalfPoints 正文字号，单位为半磅
     */
    public DocxRendererOptions(
            DocxCompatibilityMode compatibilityMode,
            String westernFontFamily,
            String eastAsiaFontFamily,
            int bodyFontSizeHalfPoints) {
        this.compatibilityMode = Objects.requireNonNull(
                compatibilityMode, "compatibilityMode 不能为空");
        this.westernFontFamily = requireFontFamily(westernFontFamily, "西文字体");
        this.eastAsiaFontFamily = requireFontFamily(eastAsiaFontFamily, "东亚字体");
        if (bodyFontSizeHalfPoints < 12 || bodyFontSizeHalfPoints > 144) {
            throw new IllegalArgumentException("正文字号必须在 12 至 144 半磅之间");
        }
        this.bodyFontSizeHalfPoints = bodyFontSizeHalfPoints;
    }

    /** @return 适合常规中西文文档的兼容配置 */
    public static DocxRendererOptions defaults() {
        return new DocxRendererOptions(
                DocxCompatibilityMode.COMPATIBLE,
                DEFAULT_WESTERN_FONT,
                DEFAULT_EAST_ASIA_FONT,
                DEFAULT_BODY_FONT_SIZE);
    }

    /** @return 非等价节点的处理方式 */
    public DocxCompatibilityMode compatibilityMode() {
        return compatibilityMode;
    }

    /** @return 西文字体名称 */
    public String westernFontFamily() {
        return westernFontFamily;
    }

    /** @return 东亚字体名称 */
    public String eastAsiaFontFamily() {
        return eastAsiaFontFamily;
    }

    /** @return 正文字号，单位为半磅 */
    public int bodyFontSizeHalfPoints() {
        return bodyFontSizeHalfPoints;
    }

    /** 规范化字体名，并拦住控制字符和异常长度。 */
    private static String requireFontFamily(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + "不能为空").trim();
        if (normalized.isEmpty() || normalized.length() > 128
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + "不是合法字体名称");
        }
        return normalized;
    }
}
