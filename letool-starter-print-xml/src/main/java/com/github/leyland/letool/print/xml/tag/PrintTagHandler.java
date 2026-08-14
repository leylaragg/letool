package com.github.leyland.letool.print.xml.tag;

import java.util.Set;

/**
 * 由可信 Java 代码显式注册的自定义标签处理器。
 *
 * @author leyland
 */
public interface PrintTagHandler {

    /** @return 稳定的小写标签名 */
    String tagName();

    /** @return 标签允许出现并返回节点的位置 */
    TagPlacement placement();

    /** @return 标签可接收的子节点模型 */
    TagContentModel contentModel();

    /** @return 标签允许的静态属性名 */
    Set<String> allowedAttributes();

    /**
     * 把静态标签声明编译为不可变计划。
     *
     * @param context 标签编译上下文
     * @return 可并发复用的标签计划
     */
    PrintTagPlan compile(TagCompileContext context);
}
