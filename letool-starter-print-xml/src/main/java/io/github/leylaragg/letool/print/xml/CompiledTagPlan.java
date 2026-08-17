package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.xml.tag.PrintTagPlan;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;

import java.util.Objects;

/**
 * 自定义标签在编译快照中的不可变执行描述。
 *
 * @author leyland
 */
final class CompiledTagPlan {

    /** 标签允许出现和返回节点的位置。 */
    private final TagPlacement placement;

    /** 标签接收的受控子节点模型。 */
    private final TagContentModel contentModel;

    /** 可信处理器生成的绑定计划。 */
    private final PrintTagPlan plan;

    /** 是否允许返回带逻辑 ID 的节点。 */
    private final boolean idsAllowed;

    /** 创建不可变标签计划描述。 */
    CompiledTagPlan(
            TagPlacement placement, TagContentModel contentModel,
            PrintTagPlan plan, boolean idsAllowed) {
        this.placement = Objects.requireNonNull(placement, "placement 不能为空");
        this.contentModel = Objects.requireNonNull(contentModel, "contentModel 不能为空");
        this.plan = Objects.requireNonNull(plan, "plan 不能为空");
        this.idsAllowed = idsAllowed;
    }

    /** @return 标签放置位置 */
    TagPlacement placement() {
        return placement;
    }

    /** @return 标签内容模型 */
    TagContentModel contentModel() {
        return contentModel;
    }

    /** @return 可信标签绑定计划 */
    PrintTagPlan plan() {
        return plan;
    }

    /** @return 是否允许返回带逻辑 ID 的节点 */
    boolean idsAllowed() {
        return idsAllowed;
    }
}
