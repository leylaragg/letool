package io.github.leylaragg.letool.print.template.inspection;

import java.util.Objects;

/**
 * 不包含模板正文的安全源码位置。
 *
 * @author leyland
 */
public final class TemplateSourceLocation {

    /** 位置所属模板代码。 */
    private final String templateCode;

    /** 从模板根节点开始的标签路径。 */
    private final String tagPath;

    /** 起始行号。 */
    private final int line;

    /** 起始列号。 */
    private final int column;

    /**
     * 创建可供宿主展示的安全位置。
     *
     * @param templateCode 稳定模板代码
     * @param tagPath 标签路径
     * @param line 正整数行号
     * @param column 正整数列号
     */
    public TemplateSourceLocation(String templateCode, String tagPath, int line, int column) {
        this.templateCode = InspectionValues.templateCode(templateCode, "templateCode");
        this.tagPath = InspectionValues.tagPath(tagPath);
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("源码行列必须为正整数");
        }
        this.line = line;
        this.column = column;
    }

    /** @return 位置所属模板代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 从模板根节点开始的标签路径 */
    public String tagPath() {
        return tagPath;
    }

    /** @return 起始行号 */
    public int line() {
        return line;
    }

    /** @return 起始列号 */
    public int column() {
        return column;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TemplateSourceLocation that)) {
            return false;
        }
        return line == that.line && column == that.column
                && templateCode.equals(that.templateCode) && tagPath.equals(that.tagPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateCode, tagPath, line, column);
    }

    /** 只输出安全标识和位置，不带模板正文。 */
    @Override
    public String toString() {
        return templateCode + ":" + tagPath + "@" + line + ":" + column;
    }
}
