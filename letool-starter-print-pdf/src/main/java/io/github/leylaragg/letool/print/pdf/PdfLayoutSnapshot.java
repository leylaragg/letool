package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.util.List;
import java.util.Map;

/**
 * 脱离排版器生命周期保存的目标和链接源位置。
 *
 * @author leyland
 */
final class PdfLayoutSnapshot {

    private final Map<String, Position> targets;
    private final Map<String, List<Position>> linkSources;
    private final float headerHeightPoints;
    private final float footerHeightPoints;

    /** 冻结当前单元提取到的目标和链接源位置。 */
    PdfLayoutSnapshot(
            Map<String, Position> targets,
            Map<String, List<Position>> linkSources,
            float headerHeightPoints,
            float footerHeightPoints) {
        this.targets = Map.copyOf(targets);
        this.linkSources = Map.copyOf(linkSources);
        this.headerHeightPoints = requireHeight(headerHeightPoints);
        this.footerHeightPoints = requireHeight(footerHeightPoints);
    }

    /** @return 当前单元内可跳转目标的位置 */
    Map<String, Position> targets() {
        return targets;
    }

    /** @return 当前单元内各链接源的可见片段 */
    Map<String, List<Position>> linkSources() {
        return linkSources;
    }

    /**
     * 确认重复区域完整落在页面边距内。
     *
     * @param layout 当前页面序列的布局
     */
    void requireRegionsFit(PageLayout layout) {
        float topMargin = micrometersToPoints(layout.margins().topMicrometers());
        float bottomMargin = micrometersToPoints(layout.margins().bottomMicrometers());
        if (headerHeightPoints > topMargin) {
            throw PrintValidationException.invalidDocument("PDF 页眉高度超过页面上边距");
        }
        if (footerHeightPoints > bottomMargin) {
            throw PrintValidationException.invalidDocument("PDF 页脚高度超过页面下边距");
        }
    }

    /** 区域高度来自排版器，异常浮点值不能进入快照。 */
    private float requireHeight(float height) {
        if (!Float.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("页面区域高度必须是非负有限值");
        }
        return height;
    }

    /** 将模型微米边距转换为 PDF 点。 */
    private float micrometersToPoints(int micrometers) {
        return micrometers * 72F / 25_400F;
    }

    /** 页面索引和页面内矩形的不可变值对象。 */
    static final class Position {
        private final int pageIndex;
        private final PDRectangle rectangle;

        /** 保存局部页码，并复制矩形以免排版器后续修改。 */
        Position(int pageIndex, PDRectangle rectangle) {
            this.pageIndex = pageIndex;
            this.rectangle = new PDRectangle(
                    rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                    rectangle.getWidth(), rectangle.getHeight());
        }

        /** @return 排版单元内的零基页码 */
        int pageIndex() {
            return pageIndex;
        }

        /** @return 与内部状态隔离的页面矩形 */
        PDRectangle rectangle() {
            return new PDRectangle(
                    rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
                    rectangle.getWidth(), rectangle.getHeight());
        }
    }
}
