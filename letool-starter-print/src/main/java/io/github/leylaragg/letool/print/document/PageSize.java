package io.github.leylaragg.letool.print.document;

/**
 * 使用整数微米表达的物理页面尺寸。
 *
 * @param widthMicrometers 页面宽度
 * @param heightMicrometers 页面高度
 * @author leyland
 */
public record PageSize(int widthMicrometers, int heightMicrometers) {

    /** 允许的最小边长：10 mm。 */
    private static final int MIN_SIDE = 10_000;

    /** 允许的最大边长：2 m。 */
    private static final int MAX_SIDE = 2_000_000;

    /** ISO A4 页面。 */
    public static final PageSize A4 = new PageSize(210_000, 297_000);

    /** 北美 Letter 页面。 */
    public static final PageSize LETTER = new PageSize(215_900, 279_400);

    /**
     * 创建页面尺寸。
     *
     * @param widthMicrometers 页面宽度
     * @param heightMicrometers 页面高度
     * @throws IllegalArgumentException 任一边长超出 10 mm 至 2 m 时抛出
     */
    public PageSize {
        if (widthMicrometers < MIN_SIDE || widthMicrometers > MAX_SIDE
                || heightMicrometers < MIN_SIDE || heightMicrometers > MAX_SIDE) {
            throw new IllegalArgumentException("页面边长必须在 10000 到 2000000 微米之间");
        }
    }
}
