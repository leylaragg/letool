package io.github.leylaragg.letool.print.template.inspection;

import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import io.github.leylaragg.letool.print.template.TemplateType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 模板编译后可供宿主预检的不可变静态契约。
 *
 * <p>该对象只保存路径、作用域和输出能力上界，不包含模板正文或模板前端内部树。</p>
 *
 * @author leyland
 */
public final class TemplateInspection {

    /** 模板稳定代码。 */
    private final String templateCode;

    /** 模板在集合中的用途。 */
    private final TemplateType templateType;

    /** 数据上下文契约版本。 */
    private final int contextVersion;

    /** 模板声明的可选输出白名单。 */
    private final Set<String> declaredOutputs;

    /** 按编译遍历顺序保存的数据路径使用。 */
    private final List<TemplatePathUsage> pathUsages;

    /** 按源码与引用顺序保存的 include 调用。 */
    private final List<TemplateIncludeUsage> includeUsages;

    /** 每个片段声明的有序参数。 */
    private final Map<String, List<String>> fragmentParameters;

    /** 模板所有分支可能生成的核心节点类型。 */
    private final Set<Class<? extends DocumentNode>> nodeTypes;

    /** 模板可能使用的公共文档特性。 */
    private final Set<DocumentFeature> features;

    /** 模板引用的格式化器名称。 */
    private final Set<String> formatters;

    /** 模板引用的表达式语言。 */
    private final Set<String> expressionLanguages;

    /** 模板引用的自定义标签。 */
    private final Set<String> customTags;

    /** 从 Builder 冻结完整检查结果。 */
    private TemplateInspection(Builder builder) {
        this.templateCode = builder.templateCode;
        this.templateType = builder.templateType;
        this.contextVersion = builder.contextVersion;
        this.declaredOutputs = immutableSet(builder.declaredOutputs);
        this.pathUsages = List.copyOf(builder.pathUsages);
        this.includeUsages = List.copyOf(builder.includeUsages);
        this.fragmentParameters = InspectionValues.fragmentParameters(builder.fragmentParameters);
        this.nodeTypes = InspectionValues.nodeTypes(builder.nodeTypes);
        this.features = immutableSet(builder.features);
        this.formatters = immutableSet(builder.formatters);
        this.expressionLanguages = immutableSet(builder.expressionLanguages);
        this.customTags = immutableSet(builder.customTags);
    }

    /**
     * 创建一个模板前端使用的检查结果 Builder。
     *
     * @param templateCode 稳定模板代码
     * @param templateType 模板用途
     * @param contextVersion 正整数上下文版本
     * @return 新的 Builder
     */
    public static Builder builder(
            String templateCode, TemplateType templateType, int contextVersion) {
        return new Builder(templateCode, templateType, contextVersion);
    }

    /** @return 模板稳定代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 模板用途 */
    public TemplateType templateType() {
        return templateType;
    }

    /** @return 数据上下文契约版本 */
    public int contextVersion() {
        return contextVersion;
    }

    /** @return 模板声明的有序输出白名单 */
    public Set<String> declaredOutputs() {
        return declaredOutputs;
    }

    /** @return 全部静态路径使用 */
    public List<TemplatePathUsage> pathUsages() {
        return pathUsages;
    }

    /** @return 全部 include 调用 */
    public List<TemplateIncludeUsage> includeUsages() {
        return includeUsages;
    }

    /** @return 片段代码到有序参数声明的映射 */
    public Map<String, List<String>> fragmentParameters() {
        return fragmentParameters;
    }

    /** @return 所有分支可能生成的核心节点类型 */
    public Set<Class<? extends DocumentNode>> nodeTypes() {
        return nodeTypes;
    }

    /** @return 模板可能使用的公共文档特性 */
    public Set<DocumentFeature> features() {
        return features;
    }

    /** @return 模板引用的格式化器名称 */
    public Set<String> formatters() {
        return formatters;
    }

    /** @return 模板引用的表达式语言 */
    public Set<String> expressionLanguages() {
        return expressionLanguages;
    }

    /** @return 模板引用的自定义标签名称 */
    public Set<String> customTags() {
        return customTags;
    }

    /** 保留插入顺序地冻结集合。 */
    private static <T> Set<T> immutableSet(Set<T> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /**
     * 模板前端逐步收集检查信息的构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 当前模板代码。 */
        private final String templateCode;

        /** 当前模板用途。 */
        private final TemplateType templateType;

        /** 当前上下文契约版本。 */
        private final int contextVersion;

        /** 按声明顺序保存输出格式。 */
        private final Set<String> declaredOutputs = new LinkedHashSet<>();

        /** 按遍历顺序保存路径读取。 */
        private final List<TemplatePathUsage> pathUsages = new ArrayList<>();

        /** 按遍历顺序保存 include 调用。 */
        private final List<TemplateIncludeUsage> includeUsages = new ArrayList<>();

        /** 按模板顺序保存片段参数。 */
        private final Map<String, List<String>> fragmentParameters = new LinkedHashMap<>();

        /** 按首次出现顺序保存节点类型。 */
        private final Set<Class<? extends DocumentNode>> nodeTypes = new LinkedHashSet<>();

        /** 按首次出现顺序保存文档特性。 */
        private final Set<DocumentFeature> features = new LinkedHashSet<>();

        /** 按首次出现顺序保存格式化器。 */
        private final Set<String> formatters = new LinkedHashSet<>();

        /** 按首次出现顺序保存表达式语言。 */
        private final Set<String> expressionLanguages = new LinkedHashSet<>();

        /** 按首次出现顺序保存自定义标签。 */
        private final Set<String> customTags = new LinkedHashSet<>();

        /** 创建并校验检查结果的稳定身份。 */
        private Builder(String templateCode, TemplateType templateType, int contextVersion) {
            this.templateCode = InspectionValues.templateCode(templateCode, "templateCode");
            this.templateType = Objects.requireNonNull(templateType, "templateType 不能为空");
            if (contextVersion < 1) {
                throw new IllegalArgumentException("contextVersion 必须为正整数");
            }
            this.contextVersion = contextVersion;
        }

        /**
         * 增加一个模板允许的输出格式。
         *
         * @param output 格式标识
         * @return 当前 Builder
         */
        public Builder declaredOutput(String output) {
            declaredOutputs.add(InspectionValues.identifier(output, "output"));
            return this;
        }

        /**
         * 增加一次静态路径使用。
         *
         * @param usage 路径使用
         * @return 当前 Builder
         */
        public Builder pathUsage(TemplatePathUsage usage) {
            pathUsages.add(Objects.requireNonNull(usage, "usage 不能为空"));
            return this;
        }

        /**
         * 增加一个 include 调用。
         *
         * @param usage include 使用
         * @return 当前 Builder
         */
        public Builder includeUsage(TemplateIncludeUsage usage) {
            includeUsages.add(Objects.requireNonNull(usage, "usage 不能为空"));
            return this;
        }

        /**
         * 保存片段公开的参数声明。
         *
         * @param fragmentCode 片段模板代码
         * @param parameters 有序参数名
         * @return 当前 Builder
         */
        public Builder fragmentParameters(String fragmentCode, List<String> parameters) {
            String code = InspectionValues.templateCode(fragmentCode, "fragmentCode");
            Objects.requireNonNull(parameters, "parameters 不能为空");
            if (fragmentParameters.putIfAbsent(code, List.copyOf(parameters)) != null) {
                throw new IllegalArgumentException("片段参数声明不能重复");
            }
            return this;
        }

        /**
         * 增加一种可能生成的核心节点。
         *
         * @param nodeType 文档节点具体类型
         * @return 当前 Builder
         */
        public Builder nodeType(Class<? extends DocumentNode> nodeType) {
            nodeTypes.add(Objects.requireNonNull(nodeType, "nodeType 不能为空"));
            return this;
        }

        /**
         * 增加一个模板可能使用的文档特性。
         *
         * @param feature 公共文档特性
         * @return 当前 Builder
         */
        public Builder feature(DocumentFeature feature) {
            features.add(Objects.requireNonNull(feature, "feature 不能为空"));
            return this;
        }

        /**
         * 记录模板引用的格式化器。
         *
         * @param formatter 格式化器名称
         * @return 当前 Builder
         */
        public Builder formatter(String formatter) {
            formatters.add(InspectionValues.identifier(formatter, "formatter"));
            return this;
        }

        /**
         * 记录模板引用的表达式语言。
         *
         * @param language 表达式语言标识
         * @return 当前 Builder
         */
        public Builder expressionLanguage(String language) {
            expressionLanguages.add(InspectionValues.identifier(language, "language"));
            return this;
        }

        /**
         * 记录模板引用的自定义标签。
         *
         * @param tagName 标签名称
         * @return 当前 Builder
         */
        public Builder customTag(String tagName) {
            customTags.add(InspectionValues.identifier(tagName, "tagName"));
            return this;
        }

        /** @return 完整的不可变检查结果 */
        public TemplateInspection build() {
            return new TemplateInspection(this);
        }
    }
}
