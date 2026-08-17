package io.github.leylaragg.letool.print.xml;

import java.util.List;

/**
 * 保存已经编译完成的 XML 片段，供多个文档安全复用。
 *
 * @author leyland
 */
final class CompiledXmlFragment {

    /** 片段对应的模板代码。 */
    private final String templateCode;

    /** 片段提供的不可变块节点。 */
    private final List<CompiledXmlNode> blocks;

    /**
     * 保存一个片段的编译结果。
     *
     * @param templateCode 片段模板代码
     * @param blocks 片段提供的块节点
     */
    CompiledXmlFragment(String templateCode, List<CompiledXmlNode> blocks) {
        this.templateCode = templateCode;
        this.blocks = List.copyOf(blocks);
    }

    /** @return 片段模板代码 */
    String templateCode() {
        return templateCode;
    }

    /** @return 不可变块节点 */
    List<CompiledXmlNode> blocks() {
        return blocks;
    }
}
