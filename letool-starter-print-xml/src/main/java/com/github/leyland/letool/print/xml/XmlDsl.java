package com.github.leyland.letool.print.xml;

/**
 * Letool 受控 XML DSL 的稳定标识和中央容量限制。
 *
 * @author leyland
 */
public final class XmlDsl {

    /** 第一版 DSL 命名空间。 */
    public static final String NAMESPACE_V1 = "https://leyland.github.io/letool/print/v1";

    /** 当前 DSL 主版本。 */
    public static final int VERSION = 1;

    /** 单个模板允许的最大 XML 节点数。 */
    public static final int MAX_NODE_COUNT = 20_000;

    /** XML 元素允许的最大嵌套深度。 */
    public static final int MAX_NODE_DEPTH = 64;

    /** 单个文本节点允许的最大字符数。 */
    public static final int MAX_TEXT_CHARACTERS = 1_000_000;

    /** 工具类不允许实例化。 */
    private XmlDsl() {
    }
}
