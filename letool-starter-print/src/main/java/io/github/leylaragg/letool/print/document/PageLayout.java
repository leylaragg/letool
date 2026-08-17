package io.github.leylaragg.letool.print.document;

import java.util.Objects;

/**
 * 页面尺寸、方向和边距组成的跨格式布局契约。
 *
 * @author leyland
 */
public final class PageLayout {

    /** 物理页面尺寸。 */
    private final PageSize pageSize;

    /** 页面方向。 */
    private final PageOrientation orientation;

    /** 页面边距。 */
    private final Margins margins;

    /**
     * 创建页面布局并检查可用内容区域。
     *
     * @param pageSize 物理页面尺寸
     * @param orientation 页面方向
     * @param margins 页面边距
     * @throws NullPointerException 任一参数为空时抛出
     * @throws IllegalArgumentException 边距之和不小于对应页面边长时抛出
     */
    public PageLayout(PageSize pageSize, PageOrientation orientation, Margins margins) {
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize 不能为空");
        this.orientation = Objects.requireNonNull(orientation, "orientation 不能为空");
        this.margins = Objects.requireNonNull(margins, "margins 不能为空");
        int width = orientation == PageOrientation.PORTRAIT
                ? pageSize.widthMicrometers() : pageSize.heightMicrometers();
        int height = orientation == PageOrientation.PORTRAIT
                ? pageSize.heightMicrometers() : pageSize.widthMicrometers();
        if ((long) margins.leftMicrometers() + margins.rightMicrometers() >= width
                || (long) margins.topMicrometers() + margins.bottomMicrometers() >= height) {
            throw new IllegalArgumentException("页面边距之和必须小于页面对应边长");
        }
    }

    /** @return 物理页面尺寸 */
    public PageSize pageSize() {
        return pageSize;
    }

    /** @return 页面方向 */
    public PageOrientation orientation() {
        return orientation;
    }

    /** @return 页面边距 */
    public Margins margins() {
        return margins;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PageLayout that)) {
            return false;
        }
        return pageSize.equals(that.pageSize)
                && orientation == that.orientation
                && margins.equals(that.margins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSize, orientation, margins);
    }

    @Override
    public String toString() {
        return "PageLayout[pageSize=" + pageSize
                + ", orientation=" + orientation
                + ", margins=" + margins + "]";
    }

    /**
     * 返回使用 20 mm 边距的 A4 纵向布局。
     *
     * @return 默认 A4 纵向布局
     */
    public static PageLayout a4Portrait() {
        return new PageLayout(
                PageSize.A4,
                PageOrientation.PORTRAIT,
                new Margins(20_000, 20_000, 20_000, 20_000));
    }
}
