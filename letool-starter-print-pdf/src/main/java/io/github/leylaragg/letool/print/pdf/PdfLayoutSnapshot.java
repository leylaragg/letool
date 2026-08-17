package io.github.leylaragg.letool.print.pdf;

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

    /** 冻结当前单元提取到的目标和链接源位置。 */
    PdfLayoutSnapshot(Map<String, Position> targets, Map<String, List<Position>> linkSources) {
        this.targets = Map.copyOf(targets);
        this.linkSources = Map.copyOf(linkSources);
    }

    /** @return 当前单元内可跳转目标的位置 */
    Map<String, Position> targets() {
        return targets;
    }

    /** @return 当前单元内各链接源的可见片段 */
    Map<String, List<Position>> linkSources() {
        return linkSources;
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
