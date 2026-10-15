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
    /** 页眉顶部距物理页顶的点数，负数表示使用默认页眉布局。 */
    private final double headerTopPoints;
    /** 页眉底部与正文之间的最小间隔，单位为点。 */
    private final double headerGapPoints;
    /** 页脚首条基线距物理页底的点数，负数表示使用默认页脚布局。 */
    private final double footerBaselinePoints;
    /** 是否将完整正文在单页可用区域内垂直居中。 */
    private final boolean centeredBody;
    /** 本页面序列的页数上限，0 表示未单独限制。 */
    private final int maxPages;

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
        this(pageSize, orientation, margins, -1, 0, -1, false, 0);
    }

    /**
     * 创建带页面区域定位和页数约束的布局。
     *
     * @param pageSize 物理页面尺寸
     * @param orientation 页面方向
     * @param margins 页面边距
     * @param headerTopPoints 页眉顶部距页顶的点数；负数使用默认布局，最小为 -1
     * @param headerGapPoints 页眉与正文的最小间隔，单位为点，必须非负
     * @param footerBaselinePoints 页脚首条基线距页底的点数；负数使用默认布局，最小为 -1
     * @param centeredBody 是否要求完整正文在单页可用区域内垂直居中
     * @param maxPages 本页面序列的页数上限；0 表示未单独限制
     * @throws NullPointerException 页面尺寸、方向或边距为 null
     * @throws IllegalArgumentException 区域参数非有限、超出范围或边距未留下正文区域
     */
    public PageLayout(PageSize pageSize, PageOrientation orientation, Margins margins,
        double headerTopPoints, double headerGapPoints, double footerBaselinePoints,
        boolean centeredBody, int maxPages) {
        if (!Double.isFinite(headerTopPoints) || headerTopPoints < -1
            || !Double.isFinite(headerGapPoints) || headerGapPoints < 0
            || !Double.isFinite(footerBaselinePoints) || footerBaselinePoints < -1 || maxPages < 0) {
            throw new IllegalArgumentException("页面区域定位参数无效");
        }
        this.headerTopPoints = headerTopPoints;
        this.headerGapPoints = headerGapPoints;
        this.footerBaselinePoints = footerBaselinePoints;
        this.centeredBody = centeredBody;
        this.maxPages = maxPages;
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

    /** @return 页眉顶部距物理页顶的点数；负数表示使用默认布局 */
    public double headerTopPoints() {
        return headerTopPoints;
    }
    /** @return 页眉与正文的最小间隔，单位为点 */
    public double headerGapPoints() {
        return headerGapPoints;
    }
    /** @return 页脚首条基线距物理页底的点数；负数表示使用默认布局 */
    public double footerBaselinePoints() {
        return footerBaselinePoints;
    }
    /** @return 是否要求完整正文在单页可用区域内垂直居中 */
    public boolean centeredBody() {
        return centeredBody;
    }
    /** @return 本页面序列的页数上限；0 表示未单独限制 */
    public int maxPages() {
        return maxPages;
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
            && margins.equals(that.margins)
            && Double.compare(headerTopPoints, that.headerTopPoints) == 0
            && Double.compare(headerGapPoints, that.headerGapPoints) == 0
            && Double.compare(footerBaselinePoints, that.footerBaselinePoints) == 0
            && centeredBody == that.centeredBody && maxPages == that.maxPages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSize, orientation, margins, headerTopPoints, headerGapPoints, footerBaselinePoints,
            centeredBody, maxPages);
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
