package com.github.leyland.letool.ruleengine.compile;

import com.github.leyland.letool.ruleengine.expression.ast.AstNode;
import com.github.leyland.letool.ruleengine.expression.ast.BetweenNode;
import com.github.leyland.letool.ruleengine.expression.ast.BinaryOperationNode;
import com.github.leyland.letool.ruleengine.expression.ast.FunctionCallNode;
import com.github.leyland.letool.ruleengine.expression.ast.ListLiteralNode;
import com.github.leyland.letool.ruleengine.expression.ast.LiteralNode;
import com.github.leyland.letool.ruleengine.expression.ast.PathNode;
import com.github.leyland.letool.ruleengine.expression.ast.UnaryOperationNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 以非递归前序规范编码计算 AST 语义指纹的包内工具。
 */
final class AstFingerprint {

    /** 工具类不允许实例化。 */
    private AstFingerprint() {
    }

    /**
     * 计算覆盖节点标签、范围、语义字段和子节点顺序的 SHA-256 指纹。
     *
     * @param root AST 根节点
     * @return 小写十六进制指纹
     */
    static String calculate(AstNode root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, "LETOOL_AST_V1");
            Deque<AstNode> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                AstNode node = pending.pop();
                addNode(digest, node);
                for (int index = node.children().size() - 1; index >= 0; index--) {
                    pending.push(node.children().get(index));
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    /** 写入节点范围和节点特有语义，不依赖对象标识。 */
    private static void addNode(MessageDigest digest, AstNode node) {
        addInt(digest, node.startPosition());
        addInt(digest, node.endPosition());
        if (node instanceof LiteralNode value) {
            add(digest, "LITERAL");
            add(digest, value.literalType().name());
            add(digest, value.normalizedValue());
        } else if (node instanceof PathNode value) {
            add(digest, "PATH");
            add(digest, value.normalizedPath());
        } else if (node instanceof FunctionCallNode value) {
            add(digest, "FUNCTION");
            add(digest, value.code());
            addInt(digest, value.arguments().size());
        } else if (node instanceof UnaryOperationNode value) {
            add(digest, "UNARY");
            add(digest, value.operator().name());
        } else if (node instanceof BinaryOperationNode value) {
            add(digest, "BINARY");
            add(digest, value.operator().name());
        } else if (node instanceof BetweenNode) {
            add(digest, "BETWEEN");
        } else {
            add(digest, "LIST");
            addInt(digest, ((ListLiteralNode) node).elements().size());
        }
        addInt(digest, node.children().size());
    }

    /** 以 UTF-8 长度前缀写入字符串，避免拼接边界歧义。 */
    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        addInt(digest, bytes.length);
        digest.update(bytes);
    }

    /** 以固定四字节编码写入整数。 */
    private static void addInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
