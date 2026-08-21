package io.github.leylaragg.letool.print.template.inspection;

import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.render.DocumentFeature;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 可信模板扩展在编译阶段声明的静态路径与输出能力。
 *
 * @author leyland
 */
public final class TemplateInspectionContribution {

    /** 不声明任何额外读取或输出语义的共享贡献。 */
    private static final TemplateInspectionContribution EMPTY = builder().build();

    /** 扩展读取的数据路径。 */
    private final Set<String> dataPaths;

    /** 扩展可能返回的核心节点类型。 */
    private final Set<Class<? extends DocumentNode>> nodeTypes;

    /** 扩展需要输出端理解的文档特性。 */
    private final Set<DocumentFeature> features;

    /** 从 Builder 创建不可变贡献。 */
    private TemplateInspectionContribution(Builder builder) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        builder.dataPaths.forEach(path -> paths.add(InspectionValues.dataPath(path)));
        this.dataPaths = Collections.unmodifiableSet(paths);
        this.nodeTypes = InspectionValues.nodeTypes(builder.nodeTypes);
        this.features = Collections.unmodifiableSet(new LinkedHashSet<>(builder.features));
    }

    /** @return 新的贡献 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 不声明额外语义的共享贡献 */
    public static TemplateInspectionContribution empty() {
        return EMPTY;
    }

    /** @return 扩展读取的有序数据路径 */
    public Set<String> dataPaths() {
        return dataPaths;
    }

    /** @return 扩展可能返回的核心节点类型 */
    public Set<Class<? extends DocumentNode>> nodeTypes() {
        return nodeTypes;
    }

    /** @return 扩展需要的公共文档特性 */
    public Set<DocumentFeature> features() {
        return features;
    }

    /**
     * 静态检查贡献构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 按扩展声明顺序保存数据路径。 */
        private final Set<String> dataPaths = new LinkedHashSet<>();

        /** 按扩展声明顺序保存节点类型。 */
        private final Set<Class<? extends DocumentNode>> nodeTypes = new LinkedHashSet<>();

        /** 按扩展声明顺序保存文档特性。 */
        private final Set<DocumentFeature> features = new LinkedHashSet<>();

        /**
         * 声明扩展读取一个数据路径。
         *
         * @param dataPath 模板前端支持的数据路径
         * @return 当前 Builder
         */
        public Builder dataPath(String dataPath) {
            dataPaths.add(InspectionValues.dataPath(dataPath));
            return this;
        }

        /**
         * 声明扩展可能返回一种核心节点。
         *
         * @param nodeType 文档节点具体类型
         * @return 当前 Builder
         */
        public Builder nodeType(Class<? extends DocumentNode> nodeType) {
            nodeTypes.add(Objects.requireNonNull(nodeType, "nodeType 不能为空"));
            return this;
        }

        /**
         * 声明扩展需要一个公共文档特性。
         *
         * @param feature 文档特性
         * @return 当前 Builder
         */
        public Builder feature(DocumentFeature feature) {
            features.add(Objects.requireNonNull(feature, "feature 不能为空"));
            return this;
        }

        /** @return 不可变静态贡献 */
        public TemplateInspectionContribution build() {
            return new TemplateInspectionContribution(this);
        }
    }
}
