package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.template.TemplateType;

/**
 * 保存 XML 源完成安全解析、尚未解析片段引用时的中间结果。
 *
 * @author leyland
 */
final class ParsedXmlTemplate {

    /** 模板用途。 */
    private final TemplateType type;

    /** 模板元数据快照。 */
    private final PrintTemplate template;

    /** 尚未解析 include 的 XML 根节点。 */
    private final CompiledXmlNode root;

    /** 当前 XML 源的元素数量。 */
    private final int nodeCount;

    /**
     * 保存单个 XML 定义的解析结果。
     *
     * @param type 模板用途
     * @param template 模板元数据
     * @param root 尚未解析 include 的根节点
     * @param nodeCount 当前 XML 源的元素数量
     */
    ParsedXmlTemplate(TemplateType type, PrintTemplate template,
                      CompiledXmlNode root, int nodeCount) {
        this.type = type;
        this.template = template;
        this.root = root;
        this.nodeCount = nodeCount;
    }

    /** @return 模板用途 */
    TemplateType type() {
        return type;
    }

    /** @return 模板元数据 */
    PrintTemplate template() {
        return template;
    }

    /** @return XML 根节点 */
    CompiledXmlNode root() {
        return root;
    }

    /** @return XML 元素数量 */
    int nodeCount() {
        return nodeCount;
    }
}
