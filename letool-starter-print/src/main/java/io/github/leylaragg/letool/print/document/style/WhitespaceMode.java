package io.github.leylaragg.letool.print.document.style;

/**
 * 段落处理原始空白字符的方式。
 *
 * @author leyland
 */
public enum WhitespaceMode {
    /** 折叠连续空白。 */
    COLLAPSE,
    /** 保留换行并折叠其他连续空白。 */
    PRESERVE_LINE_BREAKS,
    /** 保留所有空格和换行。 */
    PRESERVE_ALL
}
