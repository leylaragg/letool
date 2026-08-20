package io.github.leylaragg.letool.print.document.style;

/**
 * 段落文字的折行方式。
 *
 * @author leyland
 */
public enum TextWrapMode {
    /** 在正常断词位置折行。 */
    NORMAL,
    /** 必要时拆分超长连续文本。 */
    BREAK_LONG_WORDS,
    /** 不主动折行。 */
    NO_WRAP
}
