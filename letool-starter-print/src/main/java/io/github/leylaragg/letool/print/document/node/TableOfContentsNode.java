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

    /** 可选书签根 ID；为空时按标题收录。 */
    private final String bookmarkRoot;
    /** 可见目录标题的逻辑 ID。 */
    private final String id;
    /** 可见目录标题的段落样式名。 */
    private final String styleName;
    /** 可选测量布局；null 使用普通目录布局。 */
    private final TableOfContentsLayout layout;
    /** 可选空目录说明；null 表示未声明。 */
    private final String emptyMessage;

    /**
     * 创建一个不含排版状态的目录声明。
     *
     * @param title 可选目录标题；{@code null} 表示不显示标题
     * @param minLevel 收录的最小标题级别
     * @param maxLevel 收录的最大标题级别
     */
    public TableOfContentsNode(String title, int minLevel, int maxLevel) {
        this(title, minLevel, maxLevel, "");
    }

    /**
     * 创建从指定书签根后代生成的目录，根本身不进入目录。
     *
     * @param title 可选目录标题；null 表示不显示标题
     * @param minLevel 收录的最小层级，范围为 1 到 6
     * @param maxLevel 收录的最大层级，范围为 1 到 6 且不小于最小层级
     * @param bookmarkRoot 可选书签根 ID；为空时按标题收录，指定时只收录该根的后代
     * @throws PrintValidationException 标题、层级或书签根 ID 不合法
     */
    public TableOfContentsNode(String title, int minLevel, int maxLevel, String bookmarkRoot) {
        this("", title, minLevel, maxLevel, bookmarkRoot, "");
    }

    /**
     * 创建带可见标题导航目标和命名样式的目录。
     *
     * @param id 目录可见标题的逻辑 ID；为空时不建立独立目标
     * @param title 可选目录标题；null 表示不显示标题
     * @param minLevel 收录的最小层级，范围为 1 到 6
     * @param maxLevel 收录的最大层级，范围为 1 到 6 且不小于最小层级
     * @param bookmarkRoot 可选书签根 ID；为空时按标题收录，指定时只收录该根的后代
     * @param styleName 目录标题的段落样式名；null 或空字符串使用默认样式
     * @throws PrintValidationException 标题、目标 ID、层级或书签根 ID 不合法
     */
    public TableOfContentsNode(String id, String title, int minLevel, int maxLevel, String bookmarkRoot,
        String styleName) {
        this(id, title, minLevel, maxLevel, bookmarkRoot, styleName, null);
    }

    /**
     * 创建可由渲染器测量精确定位的目录。
     *
     * @param id 目录可见标题的逻辑 ID；为空时不建立独立目标
     * @param title 可选目录标题；null 表示不显示标题
     * @param minLevel 收录的最小层级，范围为 1 到 6
     * @param maxLevel 收录的最大层级，范围为 1 到 6 且不小于最小层级
     * @param bookmarkRoot 可选书签根 ID；为空时按标题收录，指定时只收录该根的后代
     * @param styleName 目录标题的段落样式名；null 或空字符串使用默认样式
     * @param layout 可选测量布局；非空时必须同时提供标题和标题 ID
     * @throws PrintValidationException 标题、目标、层级或测量布局前置条件不合法
     */
    public TableOfContentsNode(String id, String title, int minLevel, int maxLevel, String bookmarkRoot,
        String styleName, TableOfContentsLayout layout) {
        this(id, title, minLevel, maxLevel, bookmarkRoot, styleName, layout, null);
    }

    /**
     * 创建包含空目录说明的完整目录声明。
     *
     * @param id 目录可见标题的逻辑 ID；为空时不建立独立目标
     * @param title 可选目录标题；null 表示不显示标题
     * @param minLevel 收录的最小层级，范围为 1 到 6
     * @param maxLevel 收录的最大层级，范围为 1 到 6 且不小于最小层级
     * @param bookmarkRoot 可选书签根 ID；为空时按标题收录，指定时只收录该根的后代
     * @param styleName 目录标题的段落样式名；null 或空字符串使用默认样式
     * @param layout 可选测量布局；非空时必须同时提供标题和标题 ID
     * @param emptyMessage 可选空目录说明；null 表示未声明，最多 1024 个字符
     * @throws PrintValidationException 标题、目标、层级、说明长度或测量布局前置条件不合法
     */
    public TableOfContentsNode(String id, String title, int minLevel, int maxLevel, String bookmarkRoot,
        String styleName, TableOfContentsLayout layout, String emptyMessage) {
        if (emptyMessage != null && emptyMessage.length() > 1024) {
            throw PrintValidationException.invalidDocument("空目录说明超过长度限制");
        }
        this.emptyMessage = emptyMessage;
        this.layout = layout;
        this.id = NodeValidation.optionalId(id);
        if (layout != null && (this.id.isEmpty() || title == null)) {
            throw PrintValidationException.invalidDocument("测量目录需要可见标题及其 ID");
        }
        this.styleName = styleName == null ? "" : styleName;
        if (!this.id.isEmpty() && title == null) {
            throw PrintValidationException.invalidDocument("目录目标需要可见标题");
        }
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
        this.bookmarkRoot = NodeValidation.optionalId(bookmarkRoot);
    }

    /** @return 可见目录标题的逻辑 ID；空字符串表示没有独立目标 */
    @Override
    public String id() {
        return id;
    }

    /** @return 目录标题段落样式名 */
    public String styleName() {
        return styleName;
    }
    /** @return 可选测量布局；null 表示使用普通目录布局 */
    public TableOfContentsLayout layout() {
        return layout;
    }
    /** @return 可选空目录说明；null 表示未声明 */
    public String emptyMessage() {
        return emptyMessage;
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

    /** @return 可选目录书签根 ID */
    public String bookmarkRoot() {
        return bookmarkRoot;
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
            && Objects.equals(emptyMessage, that.emptyMessage) && Objects.equals(layout,
            that.layout) && id.equals(that.id) && styleName.equals(that.styleName) && Objects.equals(title,
            that.title) && bookmarkRoot.equals(that.bookmarkRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, minLevel, maxLevel, bookmarkRoot, styleName, layout, emptyMessage);
    }

    @Override
    public String toString() {
        return "TableOfContentsNode[titlePresent=" + (title != null)
                + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel + "]";
    }
}
