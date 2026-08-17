package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.DocumentTraversal;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 为一次 DOCX 渲染分配与业务 ID 解耦的书签名称和数字 ID。
 *
 * @author leyland
 */
final class DocxRenderIds {

    /** 逻辑 ID 到安全 Word 书签名的映射。 */
    private final Map<String, String> logicalNames;

    /** 无逻辑 ID 的标题也需要目录锚点，因此按对象身份保存名称。 */
    private final Map<HeadingNode, String> headingNames;

    /** Word 书签范围使用的下一个数字 ID。 */
    private BigInteger nextBookmarkId = BigInteger.ONE;

    /** 保存已经完成的请求内映射。 */
    private DocxRenderIds(
            Map<String, String> logicalNames, Map<HeadingNode, String> headingNames) {
        this.logicalNames = Map.copyOf(logicalNames);
        this.headingNames = Collections.unmodifiableMap(new IdentityHashMap<>(headingNames));
    }

    /**
     * 按文档遍历顺序建立稳定名称。
     *
     * @param document 当前文档
     * @return 请求内 ID 快照
     */
    static DocxRenderIds create(DocumentModel document) {
        Map<String, String> logicalNames = new LinkedHashMap<>();
        Map<HeadingNode, String> headingNames = new IdentityHashMap<>();
        int nextName = 1;
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            String name = null;
            if (!node.id().isEmpty()) {
                name = "letool_bookmark_" + nextName++;
                logicalNames.put(node.id(), name);
            }
            if (node instanceof HeadingNode heading) {
                if (name == null) {
                    name = "letool_bookmark_" + nextName++;
                }
                headingNames.put(heading, name);
            }
        }
        return new DocxRenderIds(logicalNames, headingNames);
    }

    /** 返回逻辑目标对应的安全名称。 */
    String targetName(String logicalId) {
        String name = logicalNames.get(logicalId);
        if (name == null) {
            throw PrintValidationException.invalidDocument("DOCX 内部链接目标不存在");
        }
        return name;
    }

    /** 返回标题的目录锚点名称。 */
    String headingName(HeadingNode heading) {
        return Objects.requireNonNull(headingNames.get(heading), "标题不属于当前文档");
    }

    /** @return 下一个 Word 书签数字 ID */
    BigInteger nextBookmarkId() {
        BigInteger current = nextBookmarkId;
        nextBookmarkId = nextBookmarkId.add(BigInteger.ONE);
        return current;
    }
}
