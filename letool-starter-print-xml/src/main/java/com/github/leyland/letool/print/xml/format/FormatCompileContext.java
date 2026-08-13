package com.github.leyland.letool.print.xml.format;

import java.util.Objects;

/**
 * 格式化器编译阶段可读取的安全模板位置。
 *
 * @author leyland
 */
public final class FormatCompileContext {

    /** 模板稳定编码。 */
    private final String templateCode;

    /** 当前标签路径。 */
    private final String tagPath;

    /** 当前标签起始行。 */
    private final int line;

    /** 当前标签起始列。 */
    private final int column;

    /**
     * 创建格式化器编译位置。
     *
     * @param templateCode 模板稳定编码
     * @param tagPath 当前标签路径
     * @param line 正整数行号
     * @param column 正整数列号
     */
    public FormatCompileContext(String templateCode, String tagPath, int line, int column) {
        this.templateCode = Objects.requireNonNull(templateCode, "templateCode 不能为空");
        this.tagPath = Objects.requireNonNull(tagPath, "tagPath 不能为空");
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("格式化器编译位置必须为正整数");
        }
        this.line = line;
        this.column = column;
    }

    /** @return 模板稳定编码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 当前标签路径 */
    public String tagPath() {
        return tagPath;
    }

    /** @return 当前标签起始行 */
    public int line() {
        return line;
    }

    /** @return 当前标签起始列 */
    public int column() {
        return column;
    }
}
