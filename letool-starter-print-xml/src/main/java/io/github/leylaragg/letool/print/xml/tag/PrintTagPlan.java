package io.github.leylaragg.letool.print.xml.tag;

import io.github.leylaragg.letool.print.document.node.DocumentNode;

/**
 * 编译后可并发复用的自定义标签计划。
 *
 * @author leyland
 */
@FunctionalInterface
public interface PrintTagPlan {

    /**
     * 绑定当前数据和受控子节点。
     *
     * @param context 标签绑定上下文
     * @return 一个核心文档节点
     */
    DocumentNode bind(TagBindingContext context);
}
