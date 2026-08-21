package io.github.leylaragg.letool.print.xml.tag;

import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;

import java.util.Objects;
import java.util.function.Function;

/**
 * 编译后可并发复用的自定义标签计划。
 *
 * @author leyland
 */
public interface PrintTagPlan {

    /**
     * 绑定当前数据和受控子节点。
     *
     * @param context 标签绑定上下文
     * @return 一个核心文档节点
     */
    DocumentNode bind(TagBindingContext context);

    /**
     * 声明标签读取的路径、可能返回的节点和额外文档特性。
     *
     * @return 不包含业务数据的静态检查贡献
     */
    TemplateInspectionContribution inspectionContribution();

    /**
     * 用一个明确节点类型创建常见的标签计划。
     *
     * @param nodeType 标签可能返回的节点类型
     * @param binder 单次绑定函数
     * @return 同时具备执行和静态检查契约的计划
     */
    static PrintTagPlan of(
            Class<? extends DocumentNode> nodeType,
            Function<TagBindingContext, ? extends DocumentNode> binder) {
        return of(TemplateInspectionContribution.builder().nodeType(nodeType).build(), binder);
    }

    /**
     * 用完整静态贡献创建标签计划。
     *
     * @param contribution 路径、节点类型和文档特性声明
     * @param binder 单次绑定函数
     * @return 同时具备执行和静态检查契约的计划
     */
    static PrintTagPlan of(
            TemplateInspectionContribution contribution,
            Function<TagBindingContext, ? extends DocumentNode> binder) {
        Objects.requireNonNull(contribution, "contribution 不能为空");
        Objects.requireNonNull(binder, "binder 不能为空");
        return new PrintTagPlan() {
            @Override
            public DocumentNode bind(TagBindingContext context) {
                return binder.apply(context);
            }

            @Override
            public TemplateInspectionContribution inspectionContribution() {
                return contribution;
            }
        };
    }
}
