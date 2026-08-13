package com.github.leyland.letool.print.document.node;

/**
 * 显式分页节点。
 *
 * @author leyland
 */
public enum PageBreakNode implements BlockNode {
    /** 唯一无状态分页实例。 */
    INSTANCE;

    /** @return 空字符串，分页节点不参与逻辑定位 */
    @Override
    public String id() {
        return "";
    }
}
