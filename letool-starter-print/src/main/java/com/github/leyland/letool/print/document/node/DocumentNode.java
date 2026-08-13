package com.github.leyland.letool.print.document.node;

/**
 * 通用文档树节点的公共契约。
 *
 * <p>节点逻辑 ID 用于书签和内部链接；不需要定位的节点返回空字符串。</p>
 *
 * @author leyland
 */
public sealed interface DocumentNode permits BlockNode, InlineNode {

    /**
     * 返回节点逻辑 ID。
     *
     * @return 合法逻辑 ID；不参与定位时返回空字符串
     */
    String id();
}
