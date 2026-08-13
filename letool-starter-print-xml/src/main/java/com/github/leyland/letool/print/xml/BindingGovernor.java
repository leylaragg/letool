package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.exception.PrintValidationException;

/**
 * 单次 XML 动态绑定使用的中央容量计数器。
 *
 * @author leyland
 */
final class BindingGovernor {

    /** 用于安全错误说明的模板代码。 */
    private final String templateCode;

    /** 已生成文档节点数。 */
    private long generatedNodes;

    /** 已生成文本字符数。 */
    private long generatedTextCharacters;

    /** 当前动态控制结构深度。 */
    private int dynamicDepth;

    /** 已执行的累计动态操作数。 */
    private long dynamicOperations;

    /** 创建单次绑定计数器。 */
    BindingGovernor(String templateCode) {
        this.templateCode = templateCode;
    }

    /** 增加最终文档节点计数。 */
    void addNodes(long increment) {
        generatedNodes = checkedAdd(
                generatedNodes, increment, XmlDsl.MAX_GENERATED_NODES, "生成节点数量超过限制");
    }

    /** 增加最终文本字符计数。 */
    void addText(long increment) {
        generatedTextCharacters = checkedAdd(
                generatedTextCharacters, increment,
                XmlDsl.MAX_GENERATED_TEXT_CHARACTERS, "生成文本字符数量超过限制");
    }

    /** 校验一个循环来源的元素数量。 */
    void checkLoopItems(int itemCount) {
        if (itemCount > XmlDsl.MAX_LOOP_ITEMS) {
            throw invalid("单次循环元素数量超过限制");
        }
    }

    /** 增加条件求值或循环迭代产生的动态操作数。 */
    void addDynamicOperations(long increment) {
        dynamicOperations = checkedAdd(
                dynamicOperations, increment,
                XmlDsl.MAX_DYNAMIC_OPERATIONS, "累计动态操作数量超过限制");
    }

    /** 进入一个动态控制结构。 */
    void enterDynamic() {
        if (dynamicDepth >= XmlDsl.MAX_DYNAMIC_DEPTH) {
            throw invalid("动态控制结构嵌套深度超过限制");
        }
        dynamicDepth++;
    }

    /** 离开一个动态控制结构。 */
    void exitDynamic() {
        dynamicDepth--;
    }

    /** 使用 long 安全累加并在修改状态前验证上限。 */
    private long checkedAdd(long current, long increment, long maximum, String detail) {
        if (increment < 0 || current > maximum - increment) {
            throw invalid(detail);
        }
        return current + increment;
    }

    /** 创建不包含业务数据的绑定容量异常。 */
    private PrintValidationException invalid(String detail) {
        return PrintValidationException.invalidDocument(templateCode + "：" + detail);
    }
}
