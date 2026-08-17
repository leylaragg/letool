package io.github.leylaragg.letool.print.xml;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 统一控制 XML 模板集合解析和片段展开的编译成本。
 *
 * @author leyland
 */
final class XmlTemplateSetGovernor {

    /** 整个集合允许解析的原始元素总数。 */
    private final long maxRawNodes;

    /** 单个文档解析全部 include 后的节点数上限。 */
    private final long maxExpandedNodes;

    /** 片段引用链深度上限。 */
    private final int maxIncludeDepth;

    /** 展开后结构深度上限。 */
    private final int maxStructureDepth;

    /** @return 使用 XML DSL 默认上限的治理器 */
    static XmlTemplateSetGovernor standard() {
        return new XmlTemplateSetGovernor(
                XmlDsl.MAX_TEMPLATE_SET_NODE_COUNT,
                XmlDsl.MAX_EXPANDED_NODE_COUNT,
                XmlDsl.MAX_INCLUDE_DEPTH,
                XmlDsl.MAX_NODE_DEPTH);
    }

    /**
     * 组合一组容量上限，包内边界测试可以传入较小数值。
     *
     * @param maxRawNodes 集合原始元素总数上限
     * @param maxExpandedNodes 单个文档展开节点数上限
     * @param maxIncludeDepth include 引用链深度上限
     * @param maxStructureDepth 文档展开后的结构深度上限
     */
    XmlTemplateSetGovernor(
            long maxRawNodes,
            long maxExpandedNodes,
            int maxIncludeDepth,
            int maxStructureDepth) {
        if (maxRawNodes <= 0 || maxExpandedNodes <= 0
                || maxIncludeDepth <= 0 || maxStructureDepth <= 0) {
            throw new IllegalArgumentException("XML 模板集合治理上限必须为正数");
        }
        this.maxRawNodes = maxRawNodes;
        this.maxExpandedNodes = maxExpandedNodes;
        this.maxIncludeDepth = maxIncludeDepth;
        this.maxStructureDepth = maxStructureDepth;
    }

    /**
     * 检查集合当前累计的原始元素数量。
     *
     * @param nodes 已解析的原始元素数量
     */
    void checkRawNodes(long nodes) {
        if (nodes > maxRawNodes) {
            throw PrintCompilationException.invalid("XML 模板集合节点数量超过 " + maxRawNodes);
        }
    }

    /**
     * 检查当前片段引用链深度。
     *
     * @param depth 当前引用链深度
     */
    void checkIncludeDepth(int depth) {
        if (depth > maxIncludeDepth) {
            throw PrintCompilationException.invalid("XML 片段引用深度超过 " + maxIncludeDepth);
        }
    }

    /**
     * 按 include 的实际出现次数检查一个文档的展开规模和结构深度。
     *
     * @param root 已经解析片段引用的文档根节点
     */
    void checkExpandedDocument(CompiledXmlNode root) {
        long nodes = 0;
        Deque<NodeFrame> stack = new ArrayDeque<>();
        stack.push(new NodeFrame(root, 1));
        while (!stack.isEmpty()) {
            NodeFrame frame = stack.pop();
            CompiledXmlNode node = frame.node();
            if ("include".equals(node.name())) {
                CompiledXmlFragment fragment = node.includedFragment();
                if (fragment == null) {
                    throw PrintCompilationException.invalid("include 尚未解析");
                }
                // include 自身不生成文档节点，片段内容沿用当前位置的结构深度。
                for (int index = fragment.blocks().size() - 1; index >= 0; index--) {
                    stack.push(new NodeFrame(fragment.blocks().get(index), frame.depth()));
                }
                continue;
            }
            if (++nodes > maxExpandedNodes) {
                throw PrintCompilationException.invalid("XML 文档展开节点数量超过 " + maxExpandedNodes);
            }
            if (!"#text".equals(node.name()) && frame.depth() > maxStructureDepth) {
                throw PrintCompilationException.invalid("XML 文档展开结构深度超过 " + maxStructureDepth);
            }
            int childDepth = "#text".equals(node.name())
                    ? frame.depth() : frame.depth() + 1;
            for (int index = node.children().size() - 1; index >= 0; index--) {
                stack.push(new NodeFrame(node.children().get(index), childDepth));
            }
        }
    }

    /** 保存迭代遍历中的节点和结构深度。 */
    private static final class NodeFrame {

        /** 当前编译节点。 */
        private final CompiledXmlNode node;

        /** 当前元素的展开深度。 */
        private final int depth;

        /**
         * 保存待访问节点及其展开深度。
         *
         * @param node 当前编译节点
         * @param depth 当前结构深度
         */
        private NodeFrame(CompiledXmlNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }

        /** @return 当前编译节点 */
        private CompiledXmlNode node() {
            return node;
        }

        /** @return 当前展开深度 */
        private int depth() {
            return depth;
        }
    }
}
