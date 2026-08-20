package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.BlockNode;

import java.util.List;

/**
 * 读取 XML 绑定器当前生成的唯一页面序列，避免测试沿用旧文档根语义。
 *
 * @author leyland
 */
final class XmlTestDocuments {

    /** 不允许实例化测试助手。 */
    private XmlTestDocuments() {
    }

    /** @return XML 默认页面序列 */
    static PageSequence sequence(DocumentModel document) {
        if (document.pageSequences().size() != 1) {
            throw new AssertionError("XML 绑定结果应只包含一个页面序列");
        }
        return document.pageSequences().get(0);
    }

    /** @return XML 默认页面序列中的正文 */
    static List<BlockNode> body(DocumentModel document) {
        return sequence(document).body();
    }
}
