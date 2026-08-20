package io.github.leylaragg.letool.print.render;

/**
 * 输出实现需要显式声明的公共文档特性。
 *
 * @author leyland
 */
public enum DocumentFeature {
    /** 一个文档包含多个页面序列。 */
    MULTIPLE_PAGE_SEQUENCES,
    /** 页面序列包含重复页眉。 */
    PAGE_HEADER,
    /** 页面序列包含重复页脚。 */
    PAGE_FOOTER,
    /** 页面序列改变默认逻辑页码规则。 */
    LOGICAL_PAGE_NUMBERING,
    /** 文档包含命名样式。 */
    NAMED_STYLES,
    /** 表格要求跨页重复表头。 */
    REPEATED_TABLE_HEADER,
    /** 表格使用非默认跨页策略。 */
    TABLE_PAGE_BREAK_POLICY,
    /** 段落使用非默认空白或折行规则。 */
    TEXT_FLOW_CONTROL
}
