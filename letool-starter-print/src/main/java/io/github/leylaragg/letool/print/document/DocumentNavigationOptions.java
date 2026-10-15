package io.github.leylaragg.letool.print.document;

import java.util.Objects;

/**
 * 文档的跨格式导航选项。
 *
 * @author leyland
 * @date 2026/10/14
 */
public final class DocumentNavigationOptions {

    /** 目的地定位方式。 */
    public enum DestinationMode {
        /** 定位到目标内容起点。 */
        CONTENT,
        /** 定位到目标所在页顶部。 */
        PAGE_TOP
    }

    /** 导航的坐标语义。 */
    private final DestinationMode destinationMode;
    /** 可选初始目标。 */
    private final String initialTarget;
    /** 初始显示书签面板。 */
    private final boolean showBookmarks;

    private DocumentNavigationOptions(Builder builder) {
        destinationMode = Objects.requireNonNull(builder.destinationMode, "destinationMode 不能为空");
        initialTarget = builder.initialTarget == null ? "" : builder.initialTarget;
        showBookmarks = builder.showBookmarks;
    }

    /** @return 保持原导航行为的默认选项 */
    public static DocumentNavigationOptions defaults() {
        return builder().build();
    }

    /** @return 使用兼容默认值的选项构建器 */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 导航的坐标语义 */
    public DestinationMode destinationMode() {
        return destinationMode;
    }

    /** @return 初始打开目标；空值沿用阅读器默认行为 */
    public String initialTarget() {
        return initialTarget;
    }

    /** @return 初始是否显示书签面板 */
    public boolean showBookmarks() {
        return showBookmarks;
    }

    /** 默认选项不修改阅读器初始视图。 */
    public static final class Builder {
        private DestinationMode destinationMode = DestinationMode.CONTENT;
        private String initialTarget = "";
        private boolean showBookmarks;

        private Builder() {
        }

        /**
         * 设置导航目标的定位方式。
         *
         * @param value 非空定位方式，默认定位到内容起点
         * @return 当前构建器
         */
        public Builder destinationMode(DestinationMode value) {
            destinationMode = value;
            return this;
        }

        /**
         * 设置初始打开的逻辑目标。
         *
         * @param value 目标 ID；null 或空字符串保持阅读器默认视图，目标在文档构建时校验
         * @return 当前构建器
         */
        public Builder initialTarget(String value) {
            initialTarget = value;
            return this;
        }

        /**
         * 设置是否在初始打开时显示书签面板。
         *
         * @param value true 表示显示，默认 false
         * @return 当前构建器
         */
        public Builder showBookmarks(boolean value) {
            showBookmarks = value;
            return this;
        }

        /**
         * 创建导航选项快照。
         *
         * @return 不可变导航选项
         * @throws NullPointerException 目的地定位方式为 null
         */
        public DocumentNavigationOptions build() {
            return new DocumentNavigationOptions(this);
        }
    }
}
