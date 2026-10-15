package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.style.TextStyle;

import java.util.Objects;

/**
 * 文档的字体和导航选项，与单次渲染的容量限制分开保存。
 *
 * @author leyland
 * @date 2026/10/14
 */
public final class DocumentOptions {
    /** 文档导航选项。 */
    private final DocumentNavigationOptions navigation;
    /** 未显式指定字体时采用的字体族，空字符串使用渲染器默认值。 */
    private final String defaultFontFamily;
    /** 缺字回退字体族，空字符串沿用渲染器默认回退。 */
    private final String glyphFallbackFamily;
    /** 是否要求最终输出嵌入完整字体。 */
    private final boolean fullFontEmbedding;
    /** 是否请求宿主提供的网络阅读优化处理。 */
    private final boolean webOptimized;

    private DocumentOptions(Builder builder) {
        navigation = Objects.requireNonNull(builder.navigation, "navigation 不能为空");
        defaultFontFamily = checkedFamily(builder.defaultFontFamily);
        glyphFallbackFamily = checkedFamily(builder.glyphFallbackFamily);
        fullFontEmbedding = builder.fullFontEmbedding;
        webOptimized = builder.webOptimized;
    }

    /** @return 使用兼容导航行为的默认选项 */
    public static DocumentOptions defaults() {
        return builder().build();
    }

    /** @return 使用兼容默认值的选项构建器 */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 文档导航选项 */
    public DocumentNavigationOptions navigation() {
        return navigation;
    }

    /** @return 默认字体族；空字符串使用渲染器默认值 */
    public String defaultFontFamily() {
        return defaultFontFamily;
    }

    /** @return 缺字回退字体族；空字符串沿用渲染器默认回退 */
    public String glyphFallbackFamily() {
        return glyphFallbackFamily;
    }

    /** @return 是否要求嵌入完整字体 */
    public boolean fullFontEmbedding() {
        return fullFontEmbedding;
    }

    /** @return 是否请求宿主执行网络阅读优化 */
    public boolean webOptimized() {
        return webOptimized;
    }

    /** 与文本样式共用字体族名称约束，空值统一为空字符串。 */
    private static String checkedFamily(String value) {
        return TextStyle.builder().fontFamily(value).build().fontFamily();
    }

    /** 文档选项构建器。 */
    public static final class Builder {
        private DocumentNavigationOptions navigation = DocumentNavigationOptions.defaults();

        private String defaultFontFamily = "";
        private String glyphFallbackFamily = "";
        private boolean fullFontEmbedding;
        private boolean webOptimized;

        /**
         * 设置未声明字体样式时使用的字体族。
         *
         * @param value 字体族名称；null 或空白沿用渲染器默认值
         * @return 当前构建器
         */
        public Builder defaultFontFamily(String value) {
            defaultFontFamily = value;
            return this;
        }

        /**
         * 设置缺字回退字体族。
         *
         * @param value 字体族名称；null 或空白沿用渲染器默认回退
         * @return 当前构建器
         */
        public Builder glyphFallbackFamily(String value) {
            glyphFallbackFamily = value;
            return this;
        }

        /**
         * 设置是否在最终输出中嵌入完整字体。
         *
         * @param value true 表示启用完整嵌入，默认 false
         * @return 当前构建器
         */
        public Builder fullFontEmbedding(boolean value) {
            fullFontEmbedding = value;
            return this;
        }

        /**
         * 请求宿主的网络阅读优化处理。
         *
         * @param value true 表示请求处理，默认 false；输出端需配置相应处理器
         * @return 当前构建器
         */
        public Builder webOptimized(boolean value) {
            webOptimized = value;
            return this;
        }

        private Builder() {
        }

        /**
         * 设置文档导航选项。
         *
         * @param value 非空导航选项，构建时校验
         * @return 当前构建器
         */
        public Builder navigation(DocumentNavigationOptions value) {
            navigation = value;
            return this;
        }

        /**
         * 创建并校验文档选项快照。
         *
         * @return 不可变文档选项
         * @throws NullPointerException 导航选项为 null
         * @throws io.github.leylaragg.letool.print.exception.PrintValidationException 字体族名称不合法
         */
        public DocumentOptions build() {
            return new DocumentOptions(this);
        }
    }
}
