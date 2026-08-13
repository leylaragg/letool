package com.github.leyland.letool.print.document;

/**
 * 使用整数微米表达的页面边距。
 *
 * @param topMicrometers 上边距
 * @param rightMicrometers 右边距
 * @param bottomMicrometers 下边距
 * @param leftMicrometers 左边距
 * @author leyland
 */
public record Margins(
        int topMicrometers,
        int rightMicrometers,
        int bottomMicrometers,
        int leftMicrometers) {

    /**
     * 创建非负页面边距。
     *
     * @param topMicrometers 上边距
     * @param rightMicrometers 右边距
     * @param bottomMicrometers 下边距
     * @param leftMicrometers 左边距
     * @throws IllegalArgumentException 任一边距为负数时抛出
     */
    public Margins {
        if (topMicrometers < 0 || rightMicrometers < 0
                || bottomMicrometers < 0 || leftMicrometers < 0) {
            throw new IllegalArgumentException("页面边距不能为负数");
        }
    }
}
