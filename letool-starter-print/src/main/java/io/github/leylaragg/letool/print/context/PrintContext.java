package io.github.leylaragg.letool.print.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

/**
 * 规范化后的只读打印上下文。
 *
 * <p>内部 JSON 树在输入和输出边界均执行深复制，因此该对象不可变且线程安全。</p>
 *
 * @author leyland
 */
public final class PrintContext {

    /** 宿主数据契约版本。 */
    private final int version;

    /** 与调用方隔离的上下文对象根节点。 */
    private final ObjectNode root;

    /** 创建已经校验的只读上下文。 */
    private PrintContext(int version, ObjectNode root) {
        this.version = version;
        this.root = root.deepCopy();
    }

    /**
     * 从对象根节点创建只读打印上下文。
     *
     * @param version 正整数上下文契约版本
     * @param root JSON 对象根节点
     * @return 与调用方节点隔离的打印上下文
     * @throws PrintValidationException 版本无效或根节点不是对象时抛出
     */
    public static PrintContext of(int version, JsonNode root) {
        if (version <= 0 || root == null || !root.isObject()) {
            throw PrintValidationException.invalidRequest(
                    "上下文版本必须为正整数且根节点必须为对象");
        }
        return new PrintContext(version, (ObjectNode) root);
    }

    /** @return 上下文契约版本 */
    public int version() {
        return version;
    }

    /**
     * 返回上下文树的独立副本。
     *
     * @return 可由调用方读取或修改且不会影响内部状态的 JSON 对象
     */
    public JsonNode root() {
        return root.deepCopy();
    }
}
