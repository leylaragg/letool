package io.github.leylaragg.letool.print.pdf.internal.layout;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.InlineText;
import io.github.leylaragg.letool.print.document.PageLayout;

/**
 * 用本次原生盒子测高计算页眉、页脚与单页正文区域。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfNativeLayout {
    /** 页面模型的微米长度换算为 PDF 点坐标。 */
    private static final double POINTS_PER_INCH = 72.0;
    private static final int MICROMETERS_PER_INCH = 25_400;
    private static final double BODY_OVERFLOW_TOLERANCE_POINTS = 0.05;

    private PdfNativeLayout() {
    }

    /**
     * 根据已完成的原生排版测量，替换 XHTML 中的页眉、页脚和正文定位规则。
     *
     * @param renderer 本轮已完成布局的渲染器，坐标采用其内部单位 dots
     * @param xhtml 本轮页面输入
     * @param layout 目标页面布局，区域偏移使用排版点
     * @return 包含测量修正样式的 XHTML
     * @throws IllegalArgumentException 页面无法容纳正文或居中正文不能保持单页
     */
    static String adjust(PdfBoxRenderer renderer, String xhtml, PageLayout layout) {
        float dots = renderer.getDotsPerPoint();
        double top = layout.margins().topMicrometers() * POINTS_PER_INCH / MICROMETERS_PER_INCH;
        double bottom = layout.margins().bottomMicrometers() * POINTS_PER_INCH / MICROMETERS_PER_INCH;
        double height = layout.pageSize().heightMicrometers() * POINTS_PER_INCH / MICROMETERS_PER_INCH;
        Box header = renderer.getSharedContext().getBoxById(PdfLayoutProcessor.HEADER_REGION_ID);
        Box footer = renderer.getSharedContext().getBoxById(PdfLayoutProcessor.FOOTER_REGION_ID);
        Box body = renderer.getSharedContext().getBoxById("letool-body-region");
        StringBuilder css = new StringBuilder("/*lt-measured*/");
        // 显式页眉定位时，正文上边距必须容纳实测页眉高度及声明的间隔。
        if (layout.headerTopPoints() >= 0 && header != null) {
            top = Math.max(top, layout.headerTopPoints() + header.getHeight() / dots + layout.headerGapPoints());
            css.append("@page{margin-top:").append(top).append("pt;}");
        }
        if (height - top - bottom <= 0) {
            throw new IllegalArgumentException("页面区域无法容纳正文");
        }

        // 页脚偏移锚定第一条可见文字基线，避免字体高度改变视觉位置。
        if (layout.footerBaselinePoints() >= 0 && footer != null) {
            double baseline = (firstBaseline(footer) - footer.getAbsY()) / dots;
            css.append("@page{@bottom-center{content:element(lt-page-footer);vertical-align:top;padding-top:")
                .append(bottom - layout.footerBaselinePoints() - baseline).append("pt;}}");
        }

        // 居中必须建立在完整正文可放入单页的前提上。
        if (layout.centeredBody() && body != null) {
            double bodyHeight = body.getHeight() / dots;
            double free = height - top - bottom - bodyHeight;
            if (free < -BODY_OVERFLOW_TOLERANCE_POINTS || renderer.getRootBox().getLayer().getPages().size() > 1) {
                throw new IllegalArgumentException("单页正文完整内容无法容纳");
            }
            css.append("#letool-body-region{margin-top:").append(Math.max(0, free / 2)).append("pt;}");
        }

        // 多轮排版替换上轮测量样式，不累积旧的定位规则。
        css.append("/*lt-measured-end*/");
        String clean = xhtml.replaceAll("/\\*lt-measured\\*/.*?/\\*lt-measured-end\\*/", "");
        return clean.replace("</style>", css + "</style>");
    }

    /**
     * 查找首个含可见文本的行盒基线。
     *
     * @param box 已排版的区域根盒
     * @return 相对文档顶部的原生排版坐标；没有可见文本时为 -1
     */
    static double firstBaseline(Box box) {
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof InlineText text && text.getWidth() > 0) {
                    return inline.getAbsY() + inline.getBaseline();
                }
                if (child instanceof Box nested) {
                    double value = firstBaseline(nested);
                    if (value >= 0) {
                        return value;
                    }
                }
            }
        }
        for (Box child : box.getChildren()) {
            double result = firstBaseline(child);
            if (result >= 0) {
                return result;
            }
        }
        return -1;
    }
}
