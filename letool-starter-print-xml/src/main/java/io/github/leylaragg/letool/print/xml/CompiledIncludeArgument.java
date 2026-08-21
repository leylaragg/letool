package io.github.leylaragg.letool.print.xml;

import java.util.Objects;

/**
 * include 在调用方作用域解析的一个显式片段参数。
 *
 * @author leyland
 */
final class CompiledIncludeArgument {

    /** 目标片段声明的参数名。 */
    private final String name;

    /** 调用方作用域中的受限数据路径。 */
    private final CompiledDataPath dataPath;

    /** 参数在调用模板中的标签路径。 */
    private final String tagPath;

    /** 参数标签的起始行。 */
    private final int line;

    /** 参数标签的起始列。 */
    private final int column;

    /**
     * 保存已经完成作用域校验的参数及其源码位置。
     *
     * @param name 目标片段参数名
     * @param dataPath 调用方作用域数据路径
     * @param tagPath 参数标签路径
     * @param line 参数标签起始行
     * @param column 参数标签起始列
     */
    CompiledIncludeArgument(
            String name, CompiledDataPath dataPath, String tagPath, int line, int column) {
        this.name = Objects.requireNonNull(name, "name 不能为空");
        this.dataPath = Objects.requireNonNull(dataPath, "dataPath 不能为空");
        this.tagPath = Objects.requireNonNull(tagPath, "tagPath 不能为空");
        this.line = line;
        this.column = column;
    }

    /** @return 目标片段参数名 */
    String name() {
        return name;
    }

    /** @return 调用方作用域数据路径 */
    CompiledDataPath dataPath() {
        return dataPath;
    }

    /** @return 参数标签路径 */
    String tagPath() {
        return tagPath;
    }

    /** @return 参数标签起始行 */
    int line() {
        return line;
    }

    /** @return 参数标签起始列 */
    int column() {
        return column;
    }
}
