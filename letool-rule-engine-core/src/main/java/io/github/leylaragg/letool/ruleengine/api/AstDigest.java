package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BetweenNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BinaryOperationNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.FunctionCallNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.ListLiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.PathNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode;
import io.github.leylaragg.letool.ruleengine.internal.digest.DigestBuilder;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 以非递归前序规范编码计算 AST 语义摘要的包内工具。
 */
final class AstDigest {

    /** 工具类不允许实例化。 */
    private AstDigest() {
    }

    /**
     * 计算覆盖节点标签、范围、语义字段和子节点顺序的内容摘要。
     *
     * @param root AST 根节点
     * @return 六十四位小写 SHA-256 摘要
     */
    static String calculate(AstNode root) {
        DigestBuilder digest = new DigestBuilder("LETOOL_AST_V1");
        Deque<AstNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            AstNode node = pending.pop();
            addNode(digest, node);
            for (int index = node.children().size() - 1; index >= 0; index--) {
                pending.push(node.children().get(index));
            }
        }
        return digest.finish();
    }

    /** 写入节点范围和节点特有语义，不依赖对象标识。 */
    private static void addNode(DigestBuilder digest, AstNode node) {
        digest.add(node.startPosition()).add(node.endPosition());
        if (node instanceof LiteralNode value) {
            digest.add("LITERAL").add(value.literalType().name()).add(value.normalizedValue());
        } else if (node instanceof PathNode value) {
            digest.add("PATH").add(value.normalizedPath());
        } else if (node instanceof FunctionCallNode value) {
            digest.add("FUNCTION").add(value.code()).add(value.arguments().size());
        } else if (node instanceof UnaryOperationNode value) {
            digest.add("UNARY").add(value.operator().name());
        } else if (node instanceof BinaryOperationNode value) {
            digest.add("BINARY").add(value.operator().name());
        } else if (node instanceof BetweenNode) {
            digest.add("BETWEEN");
        } else {
            digest.add("LIST").add(((ListLiteralNode) node).elements().size());
        }
        digest.add(node.children().size());
    }
}
