package io.github.leylaragg.letool.print.render;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Set;

/**
 * 某个文档渲染器能够处理的节点类型集合。
 *
 * <p>能力对象不可变且线程安全；节点检查复用文档模型的唯一遍历实现。</p>
 *
 * @author leyland
 */
public final class OutputCapability {

    /** 渲染器支持的节点具体类型。 */
    private final Set<Class<? extends DocumentNode>> supportedNodeTypes;

    /**
     * 创建输出能力。
     *
     * @param supportedNodeTypes 非空支持类型集合
     */
    public OutputCapability(Set<Class<? extends DocumentNode>> supportedNodeTypes) {
        this.supportedNodeTypes = Set.copyOf(supportedNodeTypes);
        if (this.supportedNodeTypes.isEmpty()) {
            throw new IllegalArgumentException("supportedNodeTypes 不能为空");
        }
    }

    /**
     * 判断是否支持具体节点。
     *
     * @param node 文档节点
     * @return 节点具体类型已声明时返回 {@code true}
     */
    public boolean supports(DocumentNode node) {
        return node != null && supportedNodeTypes.contains(node.getClass());
    }

    /**
     * 要求支持文档中的每个节点。
     *
     * @param document 通用文档模型
     * @throws PrintValidationException 发现不支持节点时抛出
     */
    public void requireSupports(DocumentModel document) {
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            if (!supports(node)) {
                throw PrintValidationException.invalidDocument(
                        "输出实现不支持节点类型：" + node.getClass().getSimpleName());
            }
        }
    }
}
