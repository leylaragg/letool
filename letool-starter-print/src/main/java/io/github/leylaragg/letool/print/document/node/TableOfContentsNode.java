package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 声明目录位置和需要收录的标题层级。
 *
 * <p>节点不保存页码或输出格式坐标，渲染器根据最终排版结果生成目录内容。</p>
 *
 * @author leyland
 */
public final class TableOfContentsNode implements BlockNode {

    /** 目录标题允许的最大字符数。 */
    private static final int MAX_TITLE_CHARACTERS = 256;

    /** 可选目录标题。 */
    private final String title;

    /** 收录的最小标题级别。 */
    private final int minLevel;

    /** 收录的最大标题级别。 */
    private final int maxLevel;

    /**
     * 创建一个不含排版状态的目录声明。
     *
     * @param title 可选目录标题；{@code null} 表示不显示标题
     * @param minLevel 收录的最小标题级别
     * @param maxLevel 收录的最大标题级别
     */
    public TableOfContentsNode(String title, int minLevel, int maxLevel) {
        if (title != null && (title.isBlank() || title.length() > MAX_TITLE_CHARACTERS)) {
            throw PrintValidationException.invalidDocument(
                    "目录标题不能为空白且不能超过 " + MAX_TITLE_CHARACTERS + " 个字符");
        }
        if (minLevel < 1 || minLevel > 6 || maxLevel < 1 || maxLevel > 6
                || minLevel > maxLevel) {
            throw PrintValidationException.invalidDocument("目录标题层级不合法");
        }
        this.title = title;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    /** 目录声明不成为业务导航目标。 */
    @Override
    public String id() {
        return "";
    }

    /** @return 可选目录标题 */
    public String title() {
        return title;
    }

    /** @return 收录的最小标题级别 */
    public int minLevel() {
        return minLevel;
    }

    /** @return 收录的最大标题级别 */
    public int maxLevel() {
        return maxLevel;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TableOfContentsNode that)) {
            return false;
        }
        return minLevel == that.minLevel
                && maxLevel == that.maxLevel
                && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, minLevel, maxLevel);
    }

    @Override
    public String toString() {
        return "TableOfContentsNode[titlePresent=" + (title != null)
                + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel + "]";
    }
}
