package com.github.leyland.letool.print.docx;

/**
 * 汇总通用文档单位到 Word 单位的换算。
 *
 * @author leyland
 */
final class DocxUnits {

    /** 工具类不需要实例。 */
    private DocxUnits() {
    }

    /**
     * 将微米换算为 Word 使用的 twip。
     *
     * @param micrometers 非负微米值
     * @return 四舍五入后的 twip
     */
    static long micrometersToTwips(int micrometers) {
        if (micrometers < 0) {
            throw new IllegalArgumentException("micrometers 不能为负数");
        }
        return Math.round(micrometers * 72.0d / 1270.0d);
    }
}
