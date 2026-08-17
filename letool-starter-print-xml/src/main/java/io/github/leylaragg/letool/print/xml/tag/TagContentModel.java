package io.github.leylaragg.letool.print.xml.tag;

/**
 * 自定义标签可接收的受控子节点模型。
 *
 * @author leyland
 */
public enum TagContentModel {
    /** 不允许任何子节点或直接文本。 */
    EMPTY,
    /** 允许框架先绑定块级子节点。 */
    BLOCKS,
    /** 允许框架先绑定行内子节点。 */
    INLINE
}
